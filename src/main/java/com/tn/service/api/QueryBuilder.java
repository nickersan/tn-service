package com.tn.service.api;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.springframework.util.MultiValueMap;

import com.tn.query.Query;
import com.tn.query.node.And;
import com.tn.query.node.Node;
import com.tn.query.node.Or;

public class QueryBuilder
{
  private static final CharSequence LOGICAL_AND = "&&";
  private static final CharSequence LOGICAL_OR = "||";
  private static final String PARAM_QUERY = "q";
  private static final String PARENTHESIS_CLOSE = ")";
  private static final String PARENTHESIS_OPEN = "(";
  private static final String TEMPLATE_EQUAL = "%s=%s";
  private static final String TEMPLATE_PARENTHESIS = "(%s)";

  private final Collection<String> fieldNames;

  public QueryBuilder(Class<?> subject, String... excludedFields)
  {
    this.fieldNames = this.fieldNames(subject, Set.of(excludedFields));
  }

  public String build(MultiValueMap<String, String> params) throws IllegalParameterException
  {
    checkParams(params);
    return params.entrySet().stream().map(this::or).collect(joining(LOGICAL_AND));
  }

  private void checkParams(MultiValueMap<String, String> params) throws IllegalParameterException
  {
    for (String paramName : params.keySet())
    {
      if (!PARAM_QUERY.equals(paramName) && !this.fieldNames.contains(paramName))
      {
        throw new IllegalParameterException("Unknown param: " + paramName);
      }
    }
  }

  private void checkQuery(Node query) throws IllegalParameterException
  {
    Object left = query.getLeft();

    if (left instanceof String)
    {
      if (!this.fieldNames.contains(left))
      {
        throw new IllegalParameterException("Unknown param: " + left);
      }
    }
    else if (left instanceof Node)
    {
      this.checkQuery((Node)left);

      if (query.getRight() instanceof Node)
      {
        this.checkQuery((Node)query.getRight());
      }
    }
  }

  private String or(Entry<String, List<String>> entry) throws IllegalParameterException
  {
    String name = entry.getKey();
    Collection<String> values = entry.getValue();
    Collector<CharSequence, ?, String> collector = values.size() == 1 ? joining(LOGICAL_OR) : joining(LOGICAL_OR, PARENTHESIS_OPEN, PARENTHESIS_CLOSE);

    return values.stream()
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(not(String::isEmpty))
      .map(value -> query(name, value))
      .collect(collector);
  }

  private String query(String name, String value) throws IllegalParameterException
  {
    if (!PARAM_QUERY.equals(name))
    {
      return String.format(TEMPLATE_EQUAL, name, value);
    }
    else
    {
      Node query = Query.parse(value);
      this.checkQuery(query);
      return !(query instanceof And) && !(query instanceof Or) ? value : String.format(TEMPLATE_PARENTHESIS, value);
    }
  }

  private Collection<String> fieldNames(Class<?> subject, Collection<String> excludedFields)
  {
    if (Object.class.equals(subject))
    {
      return Collections.emptySet();
    }
    else
    {
      Collection<String> fieldNames = Stream.of(subject.getDeclaredFields())
        .map(Field::getName)
        .filter(not(excludedFields::contains))
        .collect(toCollection(HashSet::new));

      fieldNames.addAll(this.fieldNames(subject.getSuperclass(), excludedFields));

      return fieldNames;
    }
  }
}