package org.example;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class StrictJson {
    @Bean Jackson2ObjectMapperBuilderCustomizer stringValuesOnly() {
        return builder->builder.postConfigurer(mapper->{
            var textual=mapper.coercionConfigFor(LogicalType.Textual);
            textual.setCoercion(CoercionInputShape.Integer,CoercionAction.Fail);
            textual.setCoercion(CoercionInputShape.Float,CoercionAction.Fail);
            textual.setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail);
        });
    }
}
