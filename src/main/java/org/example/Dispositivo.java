package org.example;

// =====================================================================
//  DISPOSITIVO — o modelo de uma linha da tabela "dispositivos"
//
//  Mesmo papel do Recado no MuralRecados: só carrega os dados e sabe
//  se transformar em JSON. Não sabe nada de SQL nem de HTTP.
//
//  Tabela no banco                 Atributo aqui
//  ------------------------------  ------------------------------
//  id_dispositivo     INT          int     id
//  nome_dispositivo   VARCHAR(30)  String  nome
//  localizacao        VARCHAR(50)  String  localizacao   (pode ser null)
//  tipo_dispositivo   VARCHAR(30)  String  tipo          ('Gateway' ou 'Endpoint')
//  gateway_pai_fk     INT          Integer gatewayPai    (pode ser null)
//
//  Mais dois atributos que NÃO são colunas: a consulta do DAO calcula
//  na hora, olhando a tabela registros.
//    ultimoRegistro → data_hora do registro mais recente (null se nunca enviou)
//    ativo          → enviou registro nos últimos 30 minutos? (decisão de aula)
// =====================================================================
public class Dispositivo {

    private final int id;
    private final String nome;
    private final String localizacao;
    private final String tipo;
    // Integer (com I maiúsculo) e não int: só o Integer aceita null.
    // O gateway não tem pai, então aqui fica null — o mesmo NULL do banco.
    private final Integer gatewayPai;
    private final String ultimoRegistro;
    private final boolean ativo;

    public Dispositivo(int id, String nome, String localizacao, String tipo,
                       Integer gatewayPai, String ultimoRegistro, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.localizacao = localizacao;
        this.tipo = tipo;
        this.gatewayPai = gatewayPai;
        this.ultimoRegistro = ultimoRegistro;
        this.ativo = ativo;
    }

    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public String getTipo() {
        return tipo;
    }

    public Integer getGatewayPai() {
        return gatewayPai;
    }

    public String getUltimoRegistro() {
        return ultimoRegistro;
    }

    // boolean usa "is" no lugar de "get": isAtivo() lê melhor que getAtivo()
    public boolean isAtivo() {
        return ativo;
    }

    // -----------------------------------------------------------------
    // paraJson(): monta o JSON que o dashboard vai receber.
    // As chaves têm o MESMO nome das colunas — o front-end não precisa
    // decorar dois vocabulários.
    //
    // Repare no gateway_pai_fk: concatenar um Integer null numa String
    // produz o texto "null", que já é o null do JSON. Por isso ele não
    // passa pelo Json.texto() — números não levam aspas.
    // -----------------------------------------------------------------
    public String paraJson() {
        return "{\"id_dispositivo\":" + id
                + ",\"nome_dispositivo\":" + Json.texto(nome)
                + ",\"localizacao\":" + Json.texto(localizacao)
                + ",\"tipo_dispositivo\":" + Json.texto(tipo)
                + ",\"gateway_pai_fk\":" + gatewayPai
                + ",\"ultimo_registro\":" + Json.texto(ultimoRegistro)
                + ",\"ativo\":" + ativo
                + "}";
    }
}
