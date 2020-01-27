package io.bazel.rules_scala.discover_tests_worker

import io.bazel.rules_scala.worker.Worker
import io.bazel.rules_scala.discover_tests_worker.DiscoveredTests.Result
import io.bazel.rules_scala.discover_tests_worker.DiscoveredTests.FrameworkDiscovery
import io.bazel.rules_scala.discover_tests_worker.DiscoveredTests.AnnotatedDiscovery
import io.bazel.rules_scala.discover_tests_worker.DiscoveredTests.SubclassDiscovery

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo

import sbt.testing.Framework
import sbt.testing.SubclassFingerprint
import sbt.testing.AnnotatedFingerprint

import java.io.FileOutputStream
import java.net.URLClassLoader
import java.nio.file.Paths

import scala.collection.JavaConverters._

object DiscoverTestsWorker extends Worker.Interface {

  def main(args: Array[String]): Unit = Worker.workerMain(args, DiscoverTestsWorker)

  def work(args: Array[String]): Unit = {
    val outputFile = Paths.get(args(0)).toFile
    val (args0, args1) = args.tail.span(_ != "--")
    val testJars = args0.map(f => Paths.get(f).toUri.toURL)
    val frameworkJars = args1.tail.map(f => Paths.get(f).toUri.toURL)

    val frameworkClassloader = new URLClassLoader(frameworkJars)
    val frameworkScanResult = (new ClassGraph)
      .overrideClassLoaders(frameworkClassloader)
      .ignoreParentClassLoaders
      .enableClassInfo.scan

    val testScanResult = (new ClassGraph)
      .overrideClassLoaders(new URLClassLoader(testJars ++ frameworkJars))
      .ignoreParentClassLoaders
      .enableClassInfo
      .enableMethodInfo
      .scan

    val result: Result = frameworkScanResult
      .getClassesImplementing("sbt.testing.Framework").asScala
      .foldLeft(Result.newBuilder) { (resultBuilder, framework) =>
        val frameworkInstance = framework.loadClass.newInstance.asInstanceOf[Framework]
        resultBuilder.addFrameworkDiscoveries(
          frameworkInstance.fingerprints.foldLeft(FrameworkDiscovery.newBuilder)((b, f) => f match {
            case fingerprint: SubclassFingerprint =>
              val tests = testScanResult
                .getClassesImplementing(fingerprint.superclassName)
                .exclude(frameworkScanResult.getClassesImplementing(fingerprint.superclassName))
                .asScala
                .filter(_.isStandardClass)
                .filter(_.getConstructorInfo.asScala.exists(_.getParameterInfo.isEmpty) == fingerprint.requireNoArgConstructor)
                .map(_.getName)
                .asJava

              b.addSubclassDiscoveries(
                SubclassDiscovery.newBuilder
                  .setSubclassName(fingerprint.superclassName)
                  .setRequireNoArgConstructor(fingerprint.requireNoArgConstructor)
                  .addAllTests(tests)
                  .build)
            case fingerprint: AnnotatedFingerprint =>
              b.addAnnotatedDiscoveries(
                AnnotatedDiscovery.newBuilder
                  .build)
          }).build)
      }.build

    testScanResult.close()
    frameworkScanResult.close()

    val os = new FileOutputStream(outputFile)
    result.writeTo(os)
    os.close()
  }
}
