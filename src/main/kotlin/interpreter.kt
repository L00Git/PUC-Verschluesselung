import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import java.util.*

sealed class Expr {
  data class Var(val name: String) : Expr()
  data class Lambda(val binder: String, val tyBinder: MonoType?, val body: Expr) : Expr()
  data class App(val func: Expr, val arg: Expr) : Expr()
  data class If(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr()
  data class Binary(val left: Expr, val op: Operator, val right: Expr) : Expr()
  data class Let(val recursive: Boolean, val binder: String, val expr: Expr, val body: Expr) : Expr()

  data class IntLiteral(val num: Int) : Expr()
  data class BoolLiteral(val bool: Boolean) : Expr()
  data class StringLiteral(val string: String) : Expr()
}

enum class Operator {
  Add,
  Subtract,
  Multiply,
  Divide,
  Equality,
  Concat,
  Higher,
  Lower,
  Mod
}

typealias Env = PersistentMap<String, Value>

sealed class Value {
  data class Int(val num: kotlin.Int) : Value()
  data class Bool(val bool: Boolean) : Value()
  data class String(val string: kotlin.String) : Value()
  data class Closure(var env: Env, val binder: kotlin.String, val body: Expr) : Value()
}

fun eval(env: Env, expr: Expr): Value {
  return when (expr) {
    is Expr.IntLiteral -> Value.Int(expr.num)
    is Expr.BoolLiteral -> Value.Bool(expr.bool)
    is Expr.StringLiteral -> Value.String(expr.string)
    is Expr.Binary -> {
      val left = eval(env, expr.left)
      val right = eval(env, expr.right)
      return when (expr.op) {
        Operator.Equality -> if (left is Value.Int && right is Value.Int) {
          Value.Bool(left.num == right.num)
        } else if (left is Value.Bool && right is Value.Bool) {
          Value.Bool(left.bool == right.bool)
        } else if (left is Value.String && right is Value.String) {
          Value.Bool(left.string == right.string)
        } else {
          throw Error("Comparing incompatible values: $left and $right")
        }
        Operator.Concat -> if (left is Value.String && right is Value.String) {
          Value.String(left.string + right.string)
        } else {
          throw Error("Can't concatenate non-string values: $left and $right")
        }
        else -> numericBinary(left, right, nameForOp(expr.op)) { x, y -> applyOp(expr.op, x, y) }
      }

    }
    is Expr.If -> {
      val condition = eval(env, expr.condition)
      if (condition !is Value.Bool) {
        throw Exception("Expected a boolean condition, but got $condition")
      }
      return if (condition.bool) {
        eval(env, expr.thenBranch)
      } else {
        eval(env, expr.elseBranch)
      }
    }
    is Expr.Let -> {
      val evaledExpr = eval(env, expr.expr)
      if (expr.recursive && evaledExpr is Value.Closure) {
        evaledExpr.env = evaledExpr.env.put(expr.binder, evaledExpr)
      }
      val extendedEnv = env.put(expr.binder, evaledExpr)
      eval(extendedEnv, expr.body)
    }
    is Expr.Lambda -> Value.Closure(env, expr.binder, expr.body)
    is Expr.Var ->
      when (expr.name) {
        "#firstChar" -> {
          val s = env["x"]!! as Value.String
          Value.String(s.string.take(1))
        }
        "#remainingChars" -> {
          val s = env["x"]!! as Value.String
          Value.String(s.string.drop(1))
        }
        "#charCode" -> {
          val s = env["x"]!! as Value.String
          Value.Int(s.string[0].code)
        }
        "#codeChar" -> {
          val x = env["x"]!! as Value.Int
          Value.String(x.num.toChar().toString())
        }
        else -> env[expr.name] ?: throw Exception("Unbound variable ${expr.name}")
      }
    is Expr.App -> {
      val func = eval(env, expr.func)
      if (func !is Value.Closure) {
        throw Exception("$func is not a function")
      } else {
        val arg = eval(env, expr.arg)
        val newEnv = func.env.put(func.binder, arg)
        eval(newEnv, func.body)
      }
    }
  }
}

fun applyOp(op: Operator, x: Int, y: Int): Value {
  return when (op) {
    Operator.Add -> Value.Int(x + y)
    Operator.Subtract -> Value.Int(x - y)
    Operator.Multiply -> Value.Int(x * y)
    Operator.Divide -> Value.Int(x / y)
    Operator.Equality -> Value.Bool(x == y)
    Operator.Higher -> Value.Bool(x > y)
    Operator.Lower -> Value.Bool(x < y)
    Operator.Mod -> Value.Int(x % y)
    else -> throw Error("Can't concat ints")
  }
}

fun nameForOp(op: Operator): String {
  return when (op) {
    Operator.Add -> "add"
    Operator.Subtract -> "subtract"
    Operator.Multiply -> "multiply"
    Operator.Divide -> "divide"
    Operator.Equality -> "compare"
    Operator.Concat -> "concat"
    Operator.Higher -> "higher"
    Operator.Lower -> "lower"
    Operator.Mod -> "modulo"
  }
}

fun numericBinary(left: Value, right: Value, operation: String, combine: (Int, Int) -> Value): Value {
  if (left is Value.Int && right is Value.Int) {
    return combine(left.num, right.num)
  } else {
    throw (Exception("Can't $operation non-numbers, $left, $right"))
  }
}

val emptyEnv: Env = persistentHashMapOf()
val initialEnv: Env = persistentHashMapOf(
  "firstChar" to Value.Closure(emptyEnv, "x",
    Expr.Var("#firstChar")
  ),
  "remainingChars" to Value.Closure(emptyEnv, "x",
    Expr.Var("#remainingChars")
  ),
  "charCode" to Value.Closure(emptyEnv, "x",
    Expr.Var("#charCode")
  ),
  "codeChar" to Value.Closure(emptyEnv, "x",
    Expr.Var("#codeChar")
  )
)
//val x = Expr.Var("x")
//val y = Expr.Var("y")
//val v = Expr.Var("v")
//val f = Expr.Var("f")
//
//val innerZ = Expr.Lambda("v", Expr.App(Expr.App(x, x), v))
//val innerZ1 = Expr.Lambda("x", Expr.App(f, innerZ))
//val z = Expr.Lambda("f", Expr.App(innerZ1, innerZ1))

// Hausaufgabe:
// Fibonacci Funktion implementieren
// fib(0) = 1
// fib(1) = 1
// fib(x) = fib (x - 1) + fib (x - 2)

fun testInputOld(input: String) {
  val expr = Parser(Lexer(input)).parseExpression()
  val ty = infer(initialContext, expr)

  println("${eval(initialEnv, expr) } : ${prettyPoly(generalize(initialContext, applySolution(ty)))}")
}

fun testInput(input: String) {
  val expr = Parser(Lexer(input)).parseExpression()
  val ty = infer(initialContext, expr)

  println("${eval(initialEnv, expr)}")
}

fun main() {
  /*testInput("""
    let hello = "Hello" in
    let world = "World" in
    let join = \s1 => \s2 => s1 # " " # s2 in
    let shout = \s => s # "!" in
    let twice = \f => \x => f (f x) in
    twice (twice shout) (join hello world)
  """.trimIndent())*/

  menu()
}
fun menu(){
  println("Menu: \n CV: Caesar Verschlüsselung \n CE: Caesar Entschlüsselung \n VV: Vigenère Verschlüsselung \n VE: Vigenère Entschlüsselung")

    val reader = Scanner(System.`in`)
  when (reader.nextLine()) {
      "CV" -> ceasarEnc()
      "CE" -> ceasarDec()
      "VV" -> viginereEnc()
      "VE" -> viginereDec()
      else -> {
        println("Bitte nur erlaubte Befehle eingeben")
        menu()
      }
  }

}

fun viginereEnc(){
  println("Vigenère Verschlüsselung")
    val reader = Scanner(System.`in`)
    print("Texteingabe: ")
    val text: String = reader.nextLine()
    print("Passwort: ")
    val pw: String = reader.nextLine()

    testInput(
      """
    let isUpper = \c => 
      ((c > 64) == (c < 91) == true)
    in
    let isLower = \c => 
      ((c > 96) == (c < 123) == true)
    in
    let shiftUpper = \c => \i =>
      if isUpper i
      then codeChar ((((c - 65) + (i - 65)) % 26 ) + 65)
      else codeChar ((((c - 65) + (i - 97)) % 26 ) + 65)
    in
    let shiftLower = \c => \i =>
      if isUpper i
      then codeChar ((((c - 97) + (i - 65)) % 26 ) + 97)
      else codeChar ((((c - 97) + (i - 97)) % 26 ) + 97)
    in
    let shift = \c => \cp => 
        let code = charCode c in
        let pCode = charCode cp in
         if isUpper code
         then shiftUpper code pCode
         else if isLower code
         then shiftLower code pCode
         else c
    in
    let rec shiftChar = \f => \s => \p => \cp =>
      if s == ""
      then ""
      else if cp == ""
        then f (firstChar s) (firstChar p)  #  shiftChar f (remainingChars s) p (remainingChars p)
        else f (firstChar s) (firstChar cp)  #  shiftChar f (remainingChars s) p (remainingChars cp)
    in
    shiftChar shift "$text" "$pw" "$pw"
  """.trimIndent()
    )
  menu()
}
fun viginereDec(){
  println("Vigenère Entschlüsselung")
  val reader = Scanner(System.`in`)
  print("Texteingabe: ")
  val text: String = reader.nextLine()
  print("Passwort: ")
  val pw: String = reader.nextLine()

  testInput(
    """
    let isUpper = \c => 
      ((c > 64) == (c < 91) == true)
    in
    let isLower = \c => 
      ((c > 96) == (c < 123) == true)
    in
    let shiftUpper = \c => \i =>
      if isUpper i
      then codeChar ((((c - 65) - (i - 65) + 26) % 26 ) + 65)
      else codeChar ((((c - 65) - (i - 97) + 26) % 26 ) + 65)
    in
    let shiftLower = \c => \i =>
      if isUpper i
      then codeChar ((((c - 97) - (i - 65) + 26) % 26 ) + 97)
      else codeChar ((((c - 97) - (i - 97) + 26) % 26 ) + 97)
    in
    let shift = \c => \cp => 
        let code = charCode c in
        let pCode = charCode cp in
         if isUpper code
         then shiftUpper code pCode
         else if isLower code
         then shiftLower code pCode
         else c
    in
    let rec shiftChar = \f => \s => \p => \cp =>
      if s == ""
      then ""
      else if cp == ""
        then f (firstChar s) (firstChar p)  #  shiftChar f (remainingChars s) p (remainingChars p)
        else f (firstChar s) (firstChar cp)  #  shiftChar f (remainingChars s) p (remainingChars cp)
    in
    shiftChar shift "$text" "$pw" "$pw"
  """.trimIndent()
  )
  menu()
}

fun ceasarEnc(){
  println("Ceasar Verschlüsselung")
  val reader = Scanner(System.`in`)
  print("Texteingabe: ")
  val text: String = reader.nextLine()
  print("Verschiebung: ")
  val number: Int = reader.nextInt()

  testInput(
    """
    let isUpper = \c => 
      ((c > 64) == (c < 91) == true)
    in
    let isLower = \c => 
      ((c > 96) == (c < 123) == true)
    in
    let shiftUpper = \c => \i =>
      codeChar ((((c - 65) + i) % 26 ) + 65)
    in
    let shiftLower = \c => \i =>
      codeChar ((((c - 97) + i) % 26 ) + 97)
    in
    let shift = \c => \i =>
        let code = charCode c in
         if isUpper code
         then shiftUpper code i
         else if isLower code
         then shiftLower code i
         else c
    in
    let rec shiftChar = \f => \s => \i =>
      if s == ""
      then ""
      else f (firstChar s) i #  shiftChar f (remainingChars s) i in
    shiftChar shift "$text" $number
  """.trimIndent()
  )
  menu()
}
fun ceasarDec(){
  println("Ceasar Entschlüsselung")
  val reader = Scanner(System.`in`)
  print("Texteingabe: ")
  val text: String = reader.nextLine()
  print("Verschiebung: ")
  val number: Int = reader.nextInt()

  testInput(
    """
    let isUpper = \c => 
      ((c > 64) == (c < 91) == true)
    in
    let isLower = \c => 
      ((c > 96) == (c < 123) == true)
    in
    let shiftUpper = \c => \i =>
      codeChar ((((c - 65) - i + 26) % 26 ) + 65)
    in
    let shiftLower = \c => \i =>
      codeChar ((((c - 97) - i + 26) % 26 ) + 97)
    in
    let shift = \c => \i =>
        let code = charCode c in
         if isUpper code
         then shiftUpper code i
         else if isLower code
         then shiftLower code i
         else c
    in
    let rec shiftChar = \f => \s => \i =>
      if s == ""
      then ""
      else f (firstChar s) i #  shiftChar f (remainingChars s) i in
    shiftChar shift "$text" $number
  """.trimIndent()
  )
  menu()
}
