package com.tn.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.annotation.Nonnull;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.ContractVerifierException;

@RequiredArgsConstructor
@SuppressWarnings("unused")
public abstract class AbstractContract implements Supplier<Collection<Contract>>
{
  private static final Collection<String> CONTRACT_METHOD_PREFIXES = List.of("contract", "should", "test");

  @Nonnull
  private final Predicate<String> testMethodPredicate;

  public AbstractContract()
  {
    this(methodName -> CONTRACT_METHOD_PREFIXES.stream().anyMatch(methodName::startsWith));
  }

  @Override
  public Collection<Contract> get()
  {
    return Stream.of(getClass().getDeclaredMethods())
      .filter(method -> Contract.class.equals(method.getReturnType()))
      .filter(method -> testMethodPredicate.test(method.getName()))
      .map(this::invokeContractMethod)
      .toList();
  }

  private Contract invokeContractMethod(Method method)
  {
    try
    {
      method.setAccessible(true);
      return (Contract)method.invoke(this);
    }
    catch (InvocationTargetException | IllegalAccessException e)
    {
      throw new ContractVerifierException("An error occurred creating contract", e);
    }
  }
}

