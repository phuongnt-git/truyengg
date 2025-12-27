package com.truyengg.config;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * GraphQL configuration for custom scalars and runtime wiring.
 */
@Slf4j
@Configuration
public class GraphQLConfig {

  /**
   * Custom UUID scalar type.
   */
  private static final GraphQLScalarType UUID_SCALAR = GraphQLScalarType.newScalar()
      .name("UUID")
      .description("UUID scalar type")
      .coercing(new Coercing<UUID, String>() {
        @Override
        public String serialize(@NotNull Object dataFetcherResult,
                                @NotNull GraphQLContext graphQLContext,
                                @NotNull Locale locale) throws CoercingSerializeException {
          if (dataFetcherResult instanceof UUID uuid) {
            return uuid.toString();
          }
          throw new CoercingSerializeException("Expected a UUID object");
        }

        @Override
        public UUID parseValue(@NotNull Object input,
                               @NotNull GraphQLContext graphQLContext,
                               @NotNull Locale locale) throws CoercingParseValueException {
          try {
            return UUID.fromString(input.toString());
          } catch (IllegalArgumentException e) {
            throw new CoercingParseValueException("Invalid UUID: " + input, e);
          }
        }

        @Override
        public UUID parseLiteral(@NotNull Value<?> input,
                                 @NotNull CoercedVariables variables,
                                 @NotNull GraphQLContext graphQLContext,
                                 @NotNull Locale locale) throws CoercingParseLiteralException {
          if (input instanceof StringValue stringValue) {
            try {
              return UUID.fromString(stringValue.getValue());
            } catch (IllegalArgumentException e) {
              throw new CoercingParseLiteralException("Invalid UUID: " + stringValue.getValue(), e);
            }
          }
          throw new CoercingParseLiteralException("Expected StringValue for UUID");
        }
      })
      .build();

  /**
   * Custom DateTime scalar type for ZonedDateTime.
   */
  private static final GraphQLScalarType DATE_TIME_SCALAR = GraphQLScalarType.newScalar()
      .name("DateTime")
      .description("ISO-8601 DateTime scalar")
      .coercing(new Coercing<ZonedDateTime, String>() {
        @Override
        public String serialize(@NotNull Object dataFetcherResult,
                                @NotNull GraphQLContext graphQLContext,
                                @NotNull Locale locale) throws CoercingSerializeException {
          if (dataFetcherResult instanceof ZonedDateTime zdt) {
            return zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
          }
          throw new CoercingSerializeException("Expected a ZonedDateTime object");
        }

        @Override
        public ZonedDateTime parseValue(@NotNull Object input,
                                        @NotNull GraphQLContext graphQLContext,
                                        @NotNull Locale locale) throws CoercingParseValueException {
          try {
            return ZonedDateTime.parse(input.toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
          } catch (DateTimeParseException e) {
            throw new CoercingParseValueException("Invalid DateTime: " + input, e);
          }
        }

        @Override
        public ZonedDateTime parseLiteral(@NotNull Value<?> input,
                                          @NotNull CoercedVariables variables,
                                          @NotNull GraphQLContext graphQLContext,
                                          @NotNull Locale locale) throws CoercingParseLiteralException {
          if (input instanceof StringValue stringValue) {
            try {
              return ZonedDateTime.parse(stringValue.getValue(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException e) {
              throw new CoercingParseLiteralException("Invalid DateTime: " + stringValue.getValue(), e);
            }
          }
          throw new CoercingParseLiteralException("Expected StringValue for DateTime");
        }
      })
      .build();

  @Bean
  public RuntimeWiringConfigurer runtimeWiringConfigurer() {
    return wiringBuilder -> wiringBuilder
        .scalar(UUID_SCALAR)
        .scalar(DATE_TIME_SCALAR)
        .scalar(ExtendedScalars.GraphQLLong);
  }
}
