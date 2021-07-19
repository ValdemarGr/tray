package tray.errors

sealed trait TrayError extends Exception {
  final override def fillInStackTrace(): Throwable = this
}

object TrayError extends TrayErrorInstances {
  final case class HttpStatusError(message: String) extends TrayError {
    override def getMessage(): String = message
  }
}

sealed abstract class TrayErrorInstances {
  
}
