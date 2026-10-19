package org.example;

// =====================================================================
//  REGISTRO — o modelo de uma linha da tabela "registros"
//  (uma leitura de sensor enviada pelo robô ou por um ESP32)
//
//  A escolha de cada tipo Java segue o tipo da coluna no MySQL:
//
//  Coluna              MySQL          Java      Por quê
//  ------------------  -------------  --------  -------------------------------
//  id_registro         BIGINT         long      BIGINT passa do limite do int
//  id_dispositivo_fk   INT            int
//  time_stamp          INT UNSIGNED   long      vai até 4.294.967.295: não cabe
//                                               no int do Java (máx. ~2,1 bilhões)
//  data_hora           DATETIME       String    só exibimos, não fazemos conta
//  temperatura...      FLOAT NOT NULL float     FLOAT do banco = float do Java
//  velocidade_vento    FLOAT (NULL)   Float     wrapper: aceita null
//  direcao_vento       INT   (NULL)   Integer   wrapper: aceita null
//  intensidade_wifi    INT   (NULL)   Integer   wrapper: aceita null
//
//  Regra de bolso: coluna NOT NULL → tipo primitivo (float, int)
//                  coluna que aceita NULL → wrapper (Float, Integer)
// =====================================================================
public class Registro {

    private final long id;
    private final int idDispositivo;
    private final long timeStamp;
    private final String dataHora;
    private final float temperatura;
    private final float umidade;
    private final float pressao;
    private final float altitude;
    private final float luminosidade;
    private final Float velocidadeVento;
    private final Integer direcaoVento;
    private final Integer intensidadeWifi;

    // -----------------------------------------------------------------
    // Um construtor só, como no Recado do MuralRecados:
    //  • registro NOVO (vindo do robô): id = 0 e dataHora = null,
    //    porque quem preenche os dois é o MySQL (AUTO_INCREMENT e DEFAULT NOW())
    //  • registro LIDO do banco: todos os valores vêm do ResultSet
    // -----------------------------------------------------------------
    public Registro(long id, int idDispositivo, long timeStamp, String dataHora,
                    float temperatura, float umidade, float pressao,
                    float altitude, float luminosidade,
                    Float velocidadeVento, Integer direcaoVento, Integer intensidadeWifi) {
        this.id = id;
        this.idDispositivo = idDispositivo;
        this.timeStamp = timeStamp;
        this.dataHora = dataHora;
        this.temperatura = temperatura;
        this.umidade = umidade;
        this.pressao = pressao;
        this.altitude = altitude;
        this.luminosidade = luminosidade;
        this.velocidadeVento = velocidadeVento;
        this.direcaoVento = direcaoVento;
        this.intensidadeWifi = intensidadeWifi;
    }

    // Getters: no IntelliJ, Alt+Insert (Mac: Cmd+N) → Getter gera todos de uma vez
    public long getId() {
        return id;
    }

    public int getIdDispositivo() {
        return idDispositivo;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getDataHora() {
        return dataHora;
    }

    public float getTemperatura() {
        return temperatura;
    }

    public float getUmidade() {
        return umidade;
    }

    public float getPressao() {
        return pressao;
    }

    public float getAltitude() {
        return altitude;
    }

    public float getLuminosidade() {
        return luminosidade;
    }

    public Float getVelocidadeVento() {
        return velocidadeVento;
    }

    public Integer getDirecaoVento() {
        return direcaoVento;
    }

    public Integer getIntensidadeWifi() {
        return intensidadeWifi;
    }

    // -----------------------------------------------------------------
    // paraJson(): os números entram sem aspas.
    // float 21.4f vira o texto "21.4"; um Float null vira "null".
    // Só data_hora é texto, então só ela passa pelo Json.texto().
    // -----------------------------------------------------------------
    public String paraJson() {
        return "{\"id_registro\":" + id
                + ",\"id_dispositivo_fk\":" + idDispositivo
                + ",\"time_stamp\":" + timeStamp
                + ",\"data_hora\":" + Json.texto(dataHora)
                + ",\"temperatura\":" + temperatura
                + ",\"umidade\":" + umidade
                + ",\"pressao\":" + pressao
                + ",\"altitude\":" + altitude
                + ",\"luminosidade\":" + luminosidade
                + ",\"velocidade_vento\":" + velocidadeVento
                + ",\"direcao_vento\":" + direcaoVento
                + ",\"intensidade_wifi\":" + intensidadeWifi
                + "}";
    }
}
