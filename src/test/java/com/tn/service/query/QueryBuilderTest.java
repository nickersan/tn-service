package com.tn.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.tn.service.IllegalParameterException;

class QueryBuilderTest
{
  @Test
  void shouldBuildQuery()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class, "id");

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("name", "X");
    params.add("name", "Y");
    params.add("name", "");
    params.add("type", "Z");

    assertEquals("(name=X||name=Y)&&type=Z", queryBuilder.build(params));
  }

  @Test
  void shouldReturnQueryParam()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "name=X");

    assertEquals("name=X", queryBuilder.build(params));
  }

  @Test
  void shouldBuildQueryWithQueryParam()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "name=X||name=Y");
    params.add("type", "Z");

    assertEquals("(name=X||name=Y)&&type=Z", queryBuilder.build(params));
  }

  @Test
  void shouldRejectUnknownParam()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("unknown", "!");

    assertThrows(IllegalParameterException.class, () -> queryBuilder.build(params));
  }

  @Test
  void shouldRejectUnknownQueryParam()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "unknown=!");

    assertThrows(IllegalParameterException.class, () -> queryBuilder.build(params));
  }

  @Test
  void shouldRejectQueryWithUnknownParamLeft()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "unknown=!||name=X");

    assertThrows(IllegalParameterException.class, () -> queryBuilder.build(params));
  }

  @Test
  void shouldRejectQueryWithUnknownParamRight()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "name=X&&unknown=!");

    assertThrows(IllegalParameterException.class, () -> queryBuilder.build(params));
  }

  @Test
  void shouldRejectExcludedParam()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class, "id");

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("id", "1");

    assertThrows(IllegalParameterException.class, () -> queryBuilder.build(params));
  }

  @Test
  void shouldRejectExcludedQueryParam()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class, "id");

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "id=1");

    assertThrows(IllegalParameterException.class, () -> queryBuilder.build(params));
  }

  @SuppressWarnings("unused")
  private static class Parent
  {
    private long id;
    private String name;
  }

  @SuppressWarnings("unused")
  private static class Subject extends Parent
  {
    private String type;
  }
}
