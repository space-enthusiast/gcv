package com.github.spaceenthusiast

class AppConfig {

    var baseServerUrl: String
        private set

    init {
        baseServerUrl = System.getenv("BASE_SERVER_URL") ?: "http://127.0.0.1:8080"
    }
}