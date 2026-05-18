rootProject.name = "FANDROPS"

include(":apps:api-server")
include(":modules:common")

fun includeBoundedContext(name: String, includeApi: Boolean = true) {
    include(":modules:$name:$name-domain")
    include(":modules:$name:$name-application")
    if (includeApi) {
        include(":modules:$name:$name-api")
    }
    include(":modules:$name:$name-infrastructure")
}

includeBoundedContext("user")
includeBoundedContext("order")
includeBoundedContext("payment")
includeBoundedContext("inventory")
includeBoundedContext("notification", includeApi = false)
includeBoundedContext("community")
