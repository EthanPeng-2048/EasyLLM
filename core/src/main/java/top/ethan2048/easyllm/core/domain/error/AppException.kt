package top.ethan2048.easyllm.core.domain.error

/**
 * 统一错误类型定义
 */
sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ApiError(message: String, val errorCode: String? = null) : AppException(message)
    class NetworkError(message: String, cause: Throwable? = null) : AppException(message, cause)
    class DataNotFoundError(message: String) : AppException(message)
    class InvalidStateError(message: String) : AppException(message)
    class ConnectionError(message: String, cause: Throwable? = null) : AppException(message, cause)
}
