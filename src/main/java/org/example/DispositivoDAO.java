package org.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// =====================================================================
//  DISPOSITIVO DAO — todo o SQL da tabela "dispositivos"
//
//  Mesma receita do RecadoDAO do MuralRecados:
//    1) escreve o SQL com ? no lugar dos valores
//    2) try (abre conexão; prepara o comando) { preenche os ?; executa }
//    3) o try-with-resources fecha tudo sozinho no final
//
//  Métodos:
//    buscarIdPorNome()      → o robô se identifica pelo NOME (é UNIQUE)
//    cadastrar()            → dispositivo novo que apareceu enviando dados
//    atualizarLocalizacao() → decisão de aula: o back-end atualiza a coordenada
//    listar()               → tabela do dashboard, já com "ativo" calculado
// =====================================================================
public class DispositivoDAO {

    // -----------------------------------------------------------------
    // Procura o id pelo nome. Devolve null se o nome não existe.
    // Por que Integer e não int? Porque int não tem como dizer
    // "não achei" — só o Integer aceita null.
    // -----------------------------------------------------------------
    public Integer buscarIdPorNome(String nome) throws SQLException {
        String sql = "SELECT id_dispositivo FROM dispositivos WHERE nome_dispositivo = ?";
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, nome);
            try (ResultSet resultado = comando.executeQuery()) {
                // if e não while: o UNIQUE garante no máximo UMA linha
                if (resultado.next()) {
                    return resultado.getInt("id_dispositivo");
                }
            }
        }
        return null;
    }

    // -----------------------------------------------------------------
    // Cadastra um dispositivo que apareceu sozinho (o robô, por exemplo).
    // Ele entra como 'Gateway' e com gateway_pai_fk NULL porque publica
    // direto na API, sem passar por outro dispositivo — é exatamente o
    // significado de "pai NULL" no script do banco.
    // Endpoints reais (que publicam via gateway) são cadastrados antes,
    // no Workbench, já apontando para o pai certo.
    // -----------------------------------------------------------------
    public void cadastrar(String nome, String localizacao) throws SQLException {
        String sql = "INSERT INTO dispositivos "
                + "(nome_dispositivo, localizacao, tipo_dispositivo, gateway_pai_fk) "
                + "VALUES (?, ?, 'Gateway', NULL)";
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, nome);
            comando.setString(2, localizacao);   // setString com null grava NULL
            comando.executeUpdate();
        }
    }

    // -----------------------------------------------------------------
    // Decisão de aula (16/10): o dispositivo envia a coordenada e o
    // back-end atualiza dispositivos.localizacao. O esquema não muda.
    // -----------------------------------------------------------------
    public void atualizarLocalizacao(int id, String localizacao) throws SQLException {
        String sql = "UPDATE dispositivos SET localizacao = ? WHERE id_dispositivo = ?";
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, localizacao);
            comando.setInt(2, id);
            comando.executeUpdate();
        }
    }

    // -----------------------------------------------------------------
    // Lista todos os dispositivos com dois valores calculados:
    //
    //  MAX(r.data_hora)  → o registro mais recente de cada dispositivo
    //                      (DATE_FORMAT só deixa o texto no formato
    //                       2026-10-19 13:03:29, igual em qualquer driver)
    //  ... >= NOW() - INTERVAL 30 MINUTE → 1 se enviou nos últimos 30 min
    //
    //  LEFT JOIN: mantém na lista quem NUNCA enviou registro
    //  (o MAX vira NULL e o dispositivo aparece como inativo).
    //  GROUP BY d.id_dispositivo: junta todos os registros de cada
    //  dispositivo numa linha só, para o MAX funcionar por dispositivo.
    // -----------------------------------------------------------------
    public List<Dispositivo> listar() throws SQLException {
        String sql = "SELECT d.id_dispositivo, d.nome_dispositivo, d.localizacao, "
                + "       d.tipo_dispositivo, d.gateway_pai_fk, "
                + "       DATE_FORMAT(MAX(r.data_hora), '%Y-%m-%d %H:%i:%s') AS ultimo_registro, "
                + "       MAX(r.data_hora) >= NOW() - INTERVAL 30 MINUTE AS ativo "
                + "FROM dispositivos d "
                + "LEFT JOIN registros r ON r.id_dispositivo_fk = d.id_dispositivo "
                + "GROUP BY d.id_dispositivo "
                + "ORDER BY d.id_dispositivo";
        List<Dispositivo> dispositivos = new ArrayList<>();
        try (Connection conexao = Conexao.abrir();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            while (resultado.next()) {
                Dispositivo dispositivo = new Dispositivo(
                        resultado.getInt("id_dispositivo"),
                        resultado.getString("nome_dispositivo"),
                        resultado.getString("localizacao"),
                        resultado.getString("tipo_dispositivo"),
                        // getInt() devolveria 0 para NULL — e 0 não é "sem pai".
                        // getObject(..., Integer.class) devolve null de verdade.
                        resultado.getObject("gateway_pai_fk", Integer.class),
                        resultado.getString("ultimo_registro"),
                        // getBoolean: 1 → true; 0 ou NULL (nunca enviou) → false
                        resultado.getBoolean("ativo")
                );
                dispositivos.add(dispositivo);
            }
        }
        return dispositivos;
    }
}
