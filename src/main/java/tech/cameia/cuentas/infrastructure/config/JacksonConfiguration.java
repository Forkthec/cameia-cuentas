package tech.cameia.cuentas.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Ajusta cómo se leen los enumerados del contrato JSON.
 *
 * <p>Una cadena vacía o en blanco en un campo enumerado (por ejemplo {@code "pronoun": ""})
 * se lee como ausente. Sin este ajuste, Jackson no puede convertirla y el cuerpo entero sale
 * como ilegible; con él, la validación del contrato la reporta en su campo como obligatoria,
 * igual que la ausencia o el {@code null}, que es lo que la persona dejó sin elegir.</p>
 *
 * <p>El ajuste es solo para enumerados: un valor que no está en la lista sigue siendo
 * inválido, y un número tampoco se acepta como la posición de un valor.</p>
 */
@Configuration
public class JacksonConfiguration {

    /**
     * Publica el ajuste para el mapeador JSON de la aplicación.
     *
     * @return personalizador que aplica las reglas de lectura de enumerados
     */
    @Bean
    public JsonMapperBuilderCustomizer enumeradosVaciosComoAusentes() {
        return JacksonConfiguration::aplicarReglas;
    }

    /**
     * Aplica las reglas a un constructor de mapeador; también lo usan las pruebas del
     * contrato, para leer el JSON exactamente como la aplicación.
     *
     * @param constructor constructor del mapeador JSON
     */
    public static void aplicarReglas(JsonMapper.Builder constructor) {
        constructor.withCoercionConfig(LogicalType.Enum, regla -> regla
                .setAcceptBlankAsEmpty(true)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull));
        // Sin esto, un número se lee como la posición del valor en el enumerado: "pronoun": 1
        // se guardaría como el segundo pronombre sin que nadie lo haya elegido.
        constructor.enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    }
}
