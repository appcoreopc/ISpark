package org.tribbloid.ispark

import org.tribbloid.ispark.interpreters.SparkInterpreter

import scala.util.parsing.combinator.JavaTokenParsers

trait MagicParsers[T] extends JavaTokenParsers {
    def string: Parser[String] = stringLiteral ^^ {
        case string => string.stripPrefix("\"").stripSuffix("\"")
    }

    def magic: Parser[T]

    def parse(input: String): Either[T, String] = {
        parseAll(magic, input) match {
            case Success(result, _) => Left(result)
            case failure: NoSuccess => Right(failure.toString)
        }
    }
}

object EmptyParsers extends MagicParsers[Unit] {
    def magic: Parser[Unit] = "" ^^^ ()
}

object EntireParsers extends MagicParsers[String] {
    def magic: Parser[String] = ".*".r
}

case class TypeSpec(code: String, verbose: Boolean)

object TypeParser extends MagicParsers[TypeSpec] {
    def magic: Parser[TypeSpec] = opt("-v" | "--verbose") ~ ".*".r ^^ {
        case verbose ~ code => TypeSpec(code, verbose.isDefined)
    }
}

object Settings {
    var projectName = "Untitled"
    var managedJars: List[java.io.File] = Nil
}

abstract class Magic[T](val name: Symbol, parser: MagicParsers[T]) {
    def apply(interpreter: SparkInterpreter, input: String): Option[String] = {
        parser.parse(input) match {
            case Left(result) =>
                handle(interpreter, result)
                None
            case Right(error) =>
                Some(error)
        }
    }

    def handle(interpreter: SparkInterpreter, result: T): Unit
}

object Magic {
    val magics = List(TypeMagic)
    val pattern = "^%([a-zA-Z_][a-zA-Z0-9_]*)(.*)\n*$".r

    def unapply(code: String): Option[(String, String, Option[Magic[_]])] = code match {
        case pattern(name, input) => Some((name, input, magics.find(_.name.name == name)))
        case _ => None
    }
}

object TypeMagic extends Magic('type, TypeParser) {
    def handle(interpreter: SparkInterpreter, spec: TypeSpec) {
        interpreter.typeInfo(spec.code, spec.verbose).foreach(println)
    }
}