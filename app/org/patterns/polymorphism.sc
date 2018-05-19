

// https://www.tutorialspoint.com/design_pattern/factory_pattern.htm
sealed trait Shape
case object Circle extends Shape
case object Rectangle extends Shape

trait Drawing[A]{
  def draw() : String
}

object Drawing{

  def apply[A](implicit inst:Drawing[A]) : Drawing[A] = inst

  implicit val circle:Drawing[Circle.type] = new Drawing[Circle.type] {
    override def draw() = "Circle"
  }

  implicit val rectangle:Drawing[Rectangle.type] = new Drawing[Rectangle.type] {
    override def draw() = "Rectangle"
  }
}


Drawing[Circle.type].draw()
Drawing[Rectangle.type].draw()

def printDrawing[Shape](author:String)(implicit shape:Drawing[Shape]) : String = {
  val figure = shape.draw()
  s"$author drawing $figure"
}

def printDrawing2[Shape : Drawing](author:String) : String = {
  val figure = Drawing[Shape].draw()
  s"$author drawing $figure"
}

printDrawing[Circle.type]("Alice")
printDrawing2[Circle.type]("Bob")