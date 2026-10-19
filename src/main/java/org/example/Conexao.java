package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// =====================================================================
//  CONEXAO — o único lugar do projeto que sabe onde está o banco
//
//  Igual à Conexao do MuralRecados. Só mudou o nome do banco:
//    mural_recados  →  atmosfera40
//
//  Todo DAO chama Conexao.abrir() para conversar com o MySQL.
//  Nenhuma outra classe conhece a URL, o usuário ou a senha.
// =====================================================================
public class Conexao {

    // jdbc:mysql  → "fale com um MySQL"
    // localhost   → o MySQL está nesta mesma máquina
    // 3306        → porta padrão do MySQL (o nosso servidor web usa outra: 8080)
    // atmosfera40 → o banco criado pelo script da aula do Prof. Lucas
    private static final String URL = "jdbc:mysql://localhost:3306/atmosfera40";

    // ATENÇÃO: confira com o MySQL do seu computador.
    // Se o root do laboratório tiver senha, coloque-a aqui.
    // (Segurança 3 da aula do banco: no futuro, trocar o root por um
    //  usuário só com as permissões que a API precisa.)
    private static final String USUARIO = "root";
    private static final String SENHA = "senaisp";

    // static → chamamos pela classe: Conexao.abrir() (nunca new Conexao())
    // retorna Connection → quem chamou recebe a conexão para rodar o SQL
    // throws SQLException → se o MySQL estiver fora do ar, o erro sobe para quem chamou
    public static Connection abrir() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }
}
