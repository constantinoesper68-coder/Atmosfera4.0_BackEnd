package org.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// =====================================================================
//  REGISTRO DAO — todo o SQL da tabela "registros" (a telemetria)
//
//    cadastrar()          → grava uma leitura que o robô/ESP32 enviou
//    listarUltimasHoras() → histórico para o gráfico do dashboard
// =====================================================================
public class RegistroDAO {

    // -----------------------------------------------------------------
    // A tabela tem 12 colunas; o INSERT escreve só 10. Duas ficam de
    // fora de propósito:
    //   id_registro → AUTO_INCREMENT: o MySQL numera sozinho
    //   data_hora   → DEFAULT NOW(): o MySQL anota a hora de chegada
    // Conte os ? com cuidado: são 10, e a ordem dos setXxx(1..10)
    // tem que bater com a ordem das colunas escritas no INSERT.
    // -----------------------------------------------------------------
    public void cadastrar(Registro registro) throws SQLException {
        String sql = "INSERT INTO registros "
                + "(id_dispositivo_fk, time_stamp, temperatura, umidade, pressao, "
                + " altitude, luminosidade, velocidade_vento, direcao_vento, intensidade_wifi) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, registro.getIdDispositivo());
            comando.setLong(2, registro.getTimeStamp());
            comando.setFloat(3, registro.getTemperatura());
            comando.setFloat(4, registro.getUmidade());
            comando.setFloat(5, registro.getPressao());
            comando.setFloat(6, registro.getAltitude());
            comando.setFloat(7, registro.getLuminosidade());
            // As três opcionais podem chegar null (placa sem anemômetro).
            // setFloat()/setInt() não aceitam null; setObject() aceita
            // e grava NULL no banco.
            comando.setObject(8, registro.getVelocidadeVento());
            comando.setObject(9, registro.getDirecaoVento());
            comando.setObject(10, registro.getIntensidadeWifi());
            comando.executeUpdate();
        }
    }

    // -----------------------------------------------------------------
    // Registros das últimas N horas, do mais antigo para o mais novo
    // (a ordem que um gráfico de linha precisa).
    // O filtro usa data_hora, que tem o índice idx_registros_data_hora
    // criado no script justamente para esta consulta.
    // O ? funciona até dentro do INTERVAL: INTERVAL ? HOUR.
    //
    // DATE_FORMAT: transforma o DATETIME em texto no formato
    // 2026-10-19 13:03:29 já no MySQL. Sem ele, o texto depende do
    // driver (há versões que devolvem 2026-10-19 13:03:29.0).
    // O apelido data_hora_texto é diferente do nome da coluna para não
    // confundir: o WHERE e o ORDER BY continuam usando o DATETIME original.
    // -----------------------------------------------------------------
    public List<Registro> listarUltimasHoras(int horas) throws SQLException {
        String sql = "SELECT id_registro, id_dispositivo_fk, time_stamp, "
                + "       DATE_FORMAT(data_hora, '%Y-%m-%d %H:%i:%s') AS data_hora_texto, "
                + "       temperatura, umidade, pressao, altitude, luminosidade, "
                + "       velocidade_vento, direcao_vento, intensidade_wifi "
                + "FROM registros "
                + "WHERE data_hora >= NOW() - INTERVAL ? HOUR "
                + "ORDER BY data_hora";
        List<Registro> registros = new ArrayList<>();
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, horas);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    Registro registro = new Registro(
                            resultado.getLong("id_registro"),
                            resultado.getInt("id_dispositivo_fk"),
                            resultado.getLong("time_stamp"),
                            resultado.getString("data_hora_texto"),
                            resultado.getFloat("temperatura"),
                            resultado.getFloat("umidade"),
                            resultado.getFloat("pressao"),
                            resultado.getFloat("altitude"),
                            resultado.getFloat("luminosidade"),
                            // colunas que aceitam NULL: getObject devolve null de verdade
                            // (getFloat devolveria 0.0 — e vento 0 não é "sem anemômetro")
                            resultado.getObject("velocidade_vento", Float.class),
                            resultado.getObject("direcao_vento", Integer.class),
                            resultado.getObject("intensidade_wifi", Integer.class)
                    );
                    registros.add(registro);
                }
            }
        }
        return registros;
    }
}
