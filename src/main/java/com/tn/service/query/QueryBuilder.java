package com.tn.service.query;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

import static com.tn.query.Query.AND;
import static com.tn.query.Query.OR;
import static com.tn.query.Query.PARENTHESIS_CLOSE;
import static com.tn.query.Query.PARENTHESIS_OPEN;
import static com.tn.query.Query.parse;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.util.MultiValueMap;

import com.tn.query.node.And;
import com.tn.query.node.Node;
import com.tn.query.node.Or;
import com.tn.service.IllegalParameterException;

public class QueryBuilder
{
  private static final String PARAM_QUERY = "q";
  private static final String TEMPLATE_EQUAL = "%s=%s";
  private static final String TEMPLATE_PARENTHESIS = "(%s)";

  private final Collection<String> fieldNames;

  public QueryBuilder(Class<?> subject, String... excludedFields)
  {
    this(fieldNames(subject, Set.of(excludedFields)));
  }

  public QueryBuilder(Collection<String> fieldNames)
  {
    this.fieldNames = fieldNames;
  }

  public String build(MultiValueMap<String, String> params) throws IllegalParameterException
  {
    checkParams(params);

    return params.entrySet().stream()
      .map(this::or)
      .collect(Collectors.joining(AND));
  }

  private void checkParams(MultiValueMap<String, String> params) throws IllegalParameterException
  {
    for (String paramName : params.keySet())
    {
      if (!PARAM_QUERY.equals(paramName) && !fieldNames.contains(paramName))
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
      if (!fieldNames.contains(left))
      {
        throw new IllegalParameterException("Unknown param: " + left);
      }
    }
    else if (left instanceof Node)
    {
      checkQuery((Node)left);
      if (query.getRight() instanceof Node) checkQuery((Node)query.getRight());
    }
  }

  private String or(Map.Entry<String, List<String>> entry) throws IllegalParameterException
  {
    String name = entry.getKey();
    Collection<String> values = entry.getValue();

    Collector<CharSequence, ?, String> collector = values.size() == 1  ? joining(OR) : joining(OR, PARENTHESIS_OPEN, PARENTHESIS_CLOSE);

    return values.stream()
      .filter(Objects::nonNull)
      .map(String::trim)
      .filter(not(String::isEmpty))
      .map(value -> query(name, value))
      .collect(collector);
  }

  private String query(String name, String value) throws IllegalParameterException
  {
    if (PARAM_QUERY.equals(name))
    {
      Node query = parse(value);
      checkQuery(query);

      return query instanceof And || query instanceof Or ? format(TEMPLATE_PARENTHESIS, value) : value;
    }
    else
    {
      return format(TEMPLATE_EQUAL, name, value);
    }
  }

  private static Collection<String> fieldNames(Class<?> subject, Collection<String> excludedFields)
  {
    if (Object.class.equals(subject)) return emptySet();

    Collection<String> fieldNames = Stream.of(subject.getDeclaredFields())
      .map(Field::getName)
      .filter(not(excludedFields::contains))
      .collect(toCollection(HashSet::new));

    fieldNames.addAll(fieldNames(subject.getSuperclass(), excludedFields));

    return fieldNames;
  }
}
