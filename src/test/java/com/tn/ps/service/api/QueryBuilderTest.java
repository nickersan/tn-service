package com.tn.ps.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class QueryBuilderTest
{
  @Test
  void shouldBuildQuery() throws Exception
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
  void shouldReturnQueryParam() throws Exception
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "name=X");

    assertEquals("name=X", queryBuilder.build(params));
  }

  @Test
  void shouldBuildQueryWithQueryParam() throws Exception
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("q", "name=X||name=Y");
    params.add("type", "Z");

    assertEquals("(name=X||name=Y)&&type=Z", queryBuilder.build(params));
  }

  @Test
  void shouldRejectUnknownParams()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("unknown", "!");

    assertThrows(IllegalParameterException.class, () -> queryBuilder.build(params));
  }

  @Test
  void shouldRejectExcludedParams()
  {
    QueryBuilder queryBuilder = new QueryBuilder(Subject.class, "id");

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("id", "1");

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
