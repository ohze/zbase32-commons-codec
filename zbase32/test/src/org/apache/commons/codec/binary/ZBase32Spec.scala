package org.apache.commons.codec.binary

import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random

class ZBase32Spec extends FlatSpec with Matchers {
  val z = new ZBase32()

  "ZBase32" should "en/decode same as SZBase32" in {
    for (_ <- 0 to 20) {
      val bytes = new Array[Byte](Random.nextInt(100))
      Random.nextBytes(bytes)
      val encoded = z.encodeAsString(bytes)
      z.decode(encoded) shouldEqual bytes //should contain theSameElementsInOrderAs
    }
  }
}
