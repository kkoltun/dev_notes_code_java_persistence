package com.hr;

class RepositoryException extends RuntimeException {
  RepositoryException(String message) {
    super(message);
  }

  RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
