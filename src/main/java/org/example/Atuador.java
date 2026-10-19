package org.example;

// =====================================================================
//  ATUADOR — o modelo de uma linha da tabela "atuadores"
//  (os LEDs LED1..LED4 de cada placa)
//
//  Coluna              MySQL                 Java
//  ------------------  --------------------  -----------------------
//  id_atuador          INT                   int     id
//  id_dispositivo_fk   INT                   int     idDispositivo
//  nome_atuador        VARCHAR(30)           String  nome
//  status_atual        BOOLEAN (TINYINT 0/1) boolean ligado
//  ultimo_comando      DATETIME (NULL)       String  ultimoComando (null até o 1º comando)
// =====================================================================
public class Atuador {

    private final int id;
    private final int idDispositivo;
    private final String nome;
    private final boolean ligado;
    private final String ultimoComando;

    public Atuador(int id, int idDispositivo, String nome, boolean ligado, String ultimoComando) {
        this.id = id;
        this.idDispositivo = idDispositivo;
        this.nome = nome;
        this.ligado = ligado;
        this.ultimoComando = ultimoComando;
    }

    public int getId() {
        return id;
    }

    public int getIdDispositivo() {
        return idDispositivo;
    }

    public String getNome() {
        return nome;
    }

    public boolean isLigado() {
        return ligado;
    }

    public String getUltimoComando() {
        return ultimoComando;
    }

    // No MySQL o BOOLEAN é guardado como 0/1; no JSON sai true/false,
    // que é o que o JavaScript entende direto num if (atuador.status_atual).
    public String paraJson() {
        return "{\"id_atuador\":" + id
                + ",\"id_dispositivo_fk\":" + idDispositivo
                + ",\"nome_atuador\":" + Json.texto(nome)
                + ",\"status_atual\":" + ligado
                + ",\"ultimo_comando\":" + Json.texto(ultimoComando)
                + "}";
    }
}
