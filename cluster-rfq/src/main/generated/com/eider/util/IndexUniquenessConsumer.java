package com.eider.util;

import java.lang.FunctionalInterface;

@FunctionalInterface
public interface IndexUniquenessConsumer<T> {
  /**
   * Accepts a type<T> value and checks that the index is valid
   */
  boolean isUnique(T t);
}
