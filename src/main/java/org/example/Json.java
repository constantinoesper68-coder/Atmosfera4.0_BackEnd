package org.example;

import java.util.List;

// =====================================================================
//  JSON — pequena caixa de ferramentas para montar JSON "na mão"
//
//  No MuralRecados, o escapar() morava dentro do Recado e o laço que
//  monta a lista "[...]" morava dentro do Main. Funcionava porque só
//  havia UM modelo. Agora temos TRÊS (Dispositivo, Registro, Atuador):
//  em vez de copiar o mesmo código três vezes, ele mora aqui uma vez só.
//
//  É uma classe utilitária, igual à Conexao: tudo static, nunca new Json().
// =====================================================================
public class Json {

    // -----------------------------------------------------------------
    // texto(): transforma uma String Java em um valor JSON
    //   "Ronaldo"  →  "\"Ronaldo\""   (com aspas)
    //   null       →  "null"          (sem aspas: é o NULL do JSON)
    // Usado para colunas que aceitam NULL, como dispositivos.localizacao.
    // -----------------------------------------------------------------
    public static String texto(String valor) {
        if (valor == null) {
            return "null";
        }
        return "\"" + escapar(valor) + "\"";
    }

    // -----------------------------------------------------------------
    // lista(): junta vários objetos JSON em um array
    //   ["{...}", "{...}"]  →  "[{...},{...}]"
    // É o mesmo laço do listar() do Main do MuralRecados:
    // a vírgula entra ANTES de cada item, menos do primeiro.
    // -----------------------------------------------------------------
    public static String lista(List<String> itens) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < itens.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(itens.get(i));
        }
        json.append("]");
        return json.toString();
    }

    // -----------------------------------------------------------------
    // escapar(): o mesmo do Recado.java do MuralRecados.
    // Sem ele, um nome com aspas — Sensor "Quadra" — quebraria o JSON.
    // A barra \ é trocada PRIMEIRO; senão ela dobraria as outras trocas.
    // -----------------------------------------------------------------
    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}
