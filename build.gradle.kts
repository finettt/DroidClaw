// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://chaquo.com/maven") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        classpath("com.chaquo.python:gradle:${libs.versions.chaquopy.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}