package org.parseq.mathservice

object expression {
  sealed trait Expr
  case class Add(left: Expr, right: Expr) extends Expr
  case class Rand(value: Int) extends Expr
}
