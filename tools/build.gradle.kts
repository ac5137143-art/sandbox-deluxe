plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
}

/** Renders demo scenes to PNG frames and prints an engine benchmark. See [DemoRenderer]. */
tasks.register<JavaExec>("demo") {
    group = "verification"
    description = "Render demo scenes to PNG frames and benchmark the engine"
    mainClass.set("dev.sandbox.tools.DemoRendererKt")
    classpath = sourceSets["main"].runtimeClasspath
    val raw = (project.findProperty("demoArgs") as String?) ?: "--scene all --out demo-out"
    args(raw.split(" ").filter { it.isNotBlank() })
}
