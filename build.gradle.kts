// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}