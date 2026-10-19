package org.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// =====================================================================
//  ATUADOR DAO — todo o SQL da tabela "atuadores" (os LEDs)
//
//    criarLedsPadrao() → toda placa nova ganha LED1..LED4
//    listar()          → painel de LEDs do dashboard e do robô
//    alterarStatus()   → liga ou desliga um LED
// =====================================================================
public class AtuadorDAO {

    // -----------------------------------------------------------------
    // "Cada placa tem 4 LEDs (LED1..LED4)" — comentário do script do banco.
    // Quando um dispositivo novo aparece, ele já nasce com os quatro.
    // Detalhe: o comando é preparado UMA vez e executado QUATRO vezes,
    // trocando só o valor dos ? a cada volta do for.
    // status_atual e ultimo_comando ficam de fora: valem FALSE e NULL
    // pelo DEFAULT da tabela.
    // -----------------------------------------------------------------
    public void criarLedsPadrao(int idDispositivo) throws SQLException {
        String sql = "INSERT INTO atuadores (id_dispositivo_fk, nome_atuador) VALUES (?, ?)";
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (int numero = 1; numero <= 4; numero++) {
                comando.setInt(1, idDispositivo);
                comando.setString(2, "LED" + numero);
                comando.executeUpdate();
            }
        }
    }

    // DATE_FORMAT: o mesmo cuidado do RegistroDAO — o texto da data sai
    // sempre como 2026-10-09 09:05:00, seja qual for o driver
    public List<Atuador> listar() throws SQLException {
        String sql = "SELECT id_atuador, id_dispositivo_fk, nome_atuador, status_atual, "
                + "       DATE_FORMAT(ultimo_comando, '%Y-%m-%d %H:%i:%s') AS ultimo_comando_texto "
                + "FROM atuadores "
                + "ORDER BY id_dispositivo_fk, nome_atuador";
        List<Atuador> atuadores = new ArrayList<>();
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                Atuador atuador = new Atuador(
                        resultado.getInt("id_atuador"),
                        resultado.getInt("id_dispositivo_fk"),
                        resultado.getString("nome_atuador"),
                        resultado.getBoolean("status_atual"),
                        resultado.getString("ultimo_comando_texto")   // NULL vira null
                );
                atuadores.add(atuador);
            }
        }
        return atuadores;
    }

    // -----------------------------------------------------------------
    // Liga/desliga e anota a hora do comando (ultimo_comando = NOW()).
    // Devolve int: quantas linhas o UPDATE alterou.
    //   1 → deu certo
    //   0 → não existe atuador com esse id (o Main responde 404)
    // É o executeUpdate() que conta as linhas para a gente.
    // -----------------------------------------------------------------
    public int alterarStatus(int idAtuador, boolean ligado) throws SQLException {
        String sql = "UPDATE atuadores SET status_atual = ?, ultimo_comando = NOW() "
                + "WHERE id_atuador = ?";
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setBoolean(1, ligado);
            comando.setInt(2, idAtuador);
            return comando.executeUpdate();
        }
    }
}
