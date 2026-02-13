tasks.register("buildPlugin") {
    group = "build"
    description = "Builds the Gno GoLand plugin zip."
    dependsOn(":gno-goland-plugin:buildPlugin")
}

tasks.register("runIde") {
    group = "ide"
    description = "Runs a sandbox GoLand instance with the plugin."
    dependsOn(":gno-goland-plugin:runIde")
}
