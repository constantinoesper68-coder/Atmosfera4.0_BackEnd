package org.example;

import java.sql.SQLException;

// =====================================================================
//  TESTE DAO — testa os três DAOs pelo console, SEM servidor web
//
//  Mesma ideia do TesteDAO do MuralRecados: antes de subir o Main,
//  confirme que conexão e SQL funcionam. Se der erro aqui, o problema
//  é banco (MySQL desligado, senha, script não rodado) — não é HTTP.
//
//  Rode com o botão ▶ ao lado do main, no IntelliJ.
// =====================================================================
public class TesteDAO {
    public static void main(String[] args) throws SQLException {
        DispositivoDAO dispositivoDAO = new DispositivoDAO();
        RegistroDAO registroDAO = new RegistroDAO();
        AtuadorDAO atuadorDAO = new AtuadorDAO();

        // 1) Procura o gateway pelo nome (o mesmo caminho que a API faz)
        Integer id = dispositivoDAO.buscarIdPorNome("Gateway Biblioteca");
        System.out.println("id do Gateway Biblioteca: " + id);

        // 2) Grava uma leitura para ele.
        //    Atenção ao f: 22.5 sozinho é double; 22.5f é float.
        //    System.currentTimeMillis() é em MILISSEGUNDOS; / 1000 vira segundos Unix.
        //    Os três null são vento, direção e Wi-Fi: gravam NULL no banco.
        Registro novo = new Registro(0, id, System.currentTimeMillis() / 1000, null,
                22.5f, 60.0f, 925.0f, 760.0f, 80.0f,
                null, null, null);
        registroDAO.cadastrar(novo);
        System.out.println("Registro gravado!");

        // 3) Lista os dispositivos (o do passo 2 agora aparece como ativo)
        System.out.println("\n--- DISPOSITIVOS ---");
        for (Dispositivo dispositivo : dispositivoDAO.listar()) {
            System.out.println(dispositivo.getNome()
                    + " | ativo: " + dispositivo.isAtivo()
                    + " | último registro: " + dispositivo.getUltimoRegistro());
        }

        // 4) Lista os registros das últimas 24 horas
        System.out.println("\n--- REGISTROS (24 h) ---");
        for (Registro registro : registroDAO.listarUltimasHoras(24)) {
            System.out.println(registro.getDataHora()
                    + " | dispositivo " + registro.getIdDispositivo()
                    + " | " + registro.getTemperatura() + " °C"
                    + " | vento: " + registro.getVelocidadeVento());
        }

        // 5) Lista os LEDs
        System.out.println("\n--- ATUADORES ---");
        for (Atuador atuador : atuadorDAO.listar()) {
            System.out.println(atuador.getNome()
                    + " do dispositivo " + atuador.getIdDispositivo()
                    + " | ligado: " + atuador.isLigado());
        }

        // 6) Mostra o JSON de um dispositivo (o que o dashboard vai receber)
        System.out.println("\n--- JSON ---");
        System.out.println(dispositivoDAO.listar().get(0).paraJson());
    }
}
