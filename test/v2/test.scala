package test.v2

import org.scalatest.FunSuite

import org.scalacheck.Properties
import org.scalacheck.Prop._

import org.junit.Test
import org.junit.Assert.assertEquals

final class TestSuiteClass extends FunSuite {
  test("method1") {
    assert(Library.method1 == "hello")
  }

  test("method2") {
    assert(Library.method2 == "world")
  }
}

object TestSuiteObject extends FunSuite {
  test("not-supported") {
    assert("hello" == "world")
  }
}

final class TestPropertiesClass extends Properties("TestPropertiesClass") {
  property("1") = 1 ?= 1
}

object TestPropertiesObject extends Properties("TestPropertiesObject") {
  property("2") = 2 ?= 2
}

final class JUnitTest {
  @Test
  def testFoo(): Unit = {
    assertEquals("Test should pass", true, true)
  }
}