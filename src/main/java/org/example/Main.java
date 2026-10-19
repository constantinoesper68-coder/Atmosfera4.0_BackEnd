package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// =====================================================================
//  PROJETO ATMOSFERA 4.0 — EQUIPE DEV · BACK-END (API)
//  Escola SENAI "Anchieta" · Prof. Orrico (back-end) · Prof. Lucas Felfoldi (banco)
//  Turma 2DEV-A · Constantino Esper, Gabriel Vinha e Gerson Tomaz
//
//  O "arquivinho no meio": o navegador e o robô NUNCA falam com o MySQL.
//  Eles fazem pedidos HTTP para este servidor, que roda o SQL (pelos
//  DAOs) e devolve JSON. Mesmo esqueleto do Main do MuralRecados:
//    • HttpServer do próprio Java (sem Spring)
//    • createContext() liga cada endereço a um método atender...()
//    • lerFormulario() lê o que chegou; responder() devolve o JSON
//
//  ROTAS
//  Método  Endereço                  Quem usa         O que faz
//  ------  ------------------------  ---------------  ------------------------------
//  GET     /api/dispositivos         dashboard        lista dispositivos + "ativo"
//  GET     /api/registros?horas=24   dashboard        histórico para o gráfico
//  POST    /api/registros            robô / ESP32     grava uma leitura
//  GET     /api/atuadores            dashboard, robô  estado dos LEDs
//  PATCH   /api/atuadores            dashboard        liga/desliga um LED
//  POST    /api/login                robô             confere a senha da página
//  GET     /  ou  /dashboard         navegador        página do dashboard
//  GET     /robo                     navegador        página do robô
//
//  Códigos de resposta: 200 ok · 201 criado · 204 sem corpo (OPTIONS)
//  400 dado inválido · 401 senha errada · 404 não achou
//  405 método não permitido · 500 erro no banco
// =====================================================================
public class Main {

    // Um DAO de cada, criado uma vez só e reaproveitado em todas as requisições
    // (no Mural era um só: private static final RecadoDAO DAO)
    private static final DispositivoDAO DISPOSITIVO_DAO = new DispositivoDAO();
    private static final RegistroDAO REGISTRO_DAO = new RegistroDAO();
    private static final AtuadorDAO ATUADOR_DAO = new AtuadorDAO();

    // Senha da página do robô. Fica AQUI, no servidor, e não no JavaScript:
    // tudo que está na página qualquer um lê pelo "Inspecionar" (aula do banco).
    private static final String SENHA_ROBO = "atmosfera40";

    // Maior período que o dashboard pode pedir (7 dias = 168 horas)
    private static final int MAXIMO_HORAS = 168;

    public static void main(String[] args) throws Exception {
        testarConexao();

        // "0.0.0.0" = aceita pedidos de qualquer placa de rede desta máquina.
        // Com isso, um ESP32 na mesma rede da escola alcança o servidor pelo
        // IP do computador (ex.: http://10.0.0.15:8080/api/registros).
        HttpServer servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);

        // Method reference: "quando chegar pedido neste endereço, chame este método"
        servidor.createContext("/api/dispositivos", Main::atenderDispositivos);
        servidor.createContext("/api/registros", Main::atenderRegistros);
        servidor.createContext("/api/atuadores", Main::atenderAtuadores);
        servidor.createContext("/api/login", Main::atenderLogin);
        servidor.createContext("/", Main::abrirPaginas);   // o resto: páginas HTML
        servidor.start();

        System.out.println("Atmosfera 4.0 no ar!");
        System.out.println("Dashboard: http://localhost:8080");
        System.out.println("Robô:      http://localhost:8080/robo");
    }

    // Abre e fecha uma conexão só para ter certeza de que o banco responde.
    // Se o MySQL estiver desligado, o programa para AQUI, com o erro no console,
    // em vez de subir um servidor que falharia em todo pedido.
    private static void testarConexao() throws SQLException {
        try (Connection ignored = Conexao.abrir()) {
            System.out.println("Banco atmosfera40 conectado!");
        }
    }


    // =================================================================
    //  1) /api/dispositivos  — só GET
    // =================================================================
    private static void atenderDispositivos(HttpExchange troca) throws IOException {
        liberarAcesso(troca);
        try {
            String metodo = troca.getRequestMethod();
            if (metodo.equals("OPTIONS")) {
                responderSemCorpo(troca);
            } else if (metodo.equals("GET")) {
                listarDispositivos(troca);
            } else {
                naoPermitido(troca, "GET, OPTIONS");
            }
        } catch (SQLException erro) {
            erro.printStackTrace();
            responder(troca, 500, "{\"erro\":\"erro ao acessar o banco\"}");
        }
    }

    private static void listarDispositivos(HttpExchange troca) throws IOException, SQLException {
        // Cada objeto vira um texto JSON; o Json.lista() junta tudo em [ ... ]
        List<String> itens = new ArrayList<>();
        for (Dispositivo dispositivo : DISPOSITIVO_DAO.listar()) {
            itens.add(dispositivo.paraJson());
        }
        responder(troca, 200, Json.lista(itens));
    }


    // =================================================================
    //  2) /api/registros  — GET (dashboard) e POST (robô / ESP32)
    // =================================================================
    private static void atenderRegistros(HttpExchange troca) throws IOException {
        liberarAcesso(troca);
        try {
            String metodo = troca.getRequestMethod();
            if (metodo.equals("OPTIONS")) {
                responderSemCorpo(troca);
            } else if (metodo.equals("GET")) {
                listarRegistros(troca);
            } else if (metodo.equals("POST")) {
                cadastrarRegistro(troca);
            } else {
                naoPermitido(troca, "GET, POST, OPTIONS");
            }
        } catch (IllegalArgumentException erro) {
            // Dado inválido (campo vazio, "abc" no lugar de número...):
            // a culpa é de quem enviou → 400, com a mensagem explicando o quê.
            responder(troca, 400, "{\"erro\":" + Json.texto(erro.getMessage()) + "}");
        } catch (SQLException erro) {
            // Erro no banco: a culpa é do servidor → 500
            erro.printStackTrace();
            responder(troca, 500, "{\"erro\":\"erro ao acessar o banco\"}");
        }
    }

    // GET /api/registros?horas=24
    // O que vem depois do ? no endereço é a "query string". Ela tem o MESMO
    // formato do corpo de um formulário (chave=valor&chave=valor), então
    // reaproveitamos o mesmo decodificador: converterParametros().
    private static void listarRegistros(HttpExchange troca) throws IOException, SQLException {
        Map<String, String> parametros = converterParametros(troca.getRequestURI().getRawQuery());
        int horas = 24;   // padrão do wireframe: últimas 24 horas
        if (!texto(parametros, "horas").isEmpty()) {
            horas = converterInteiro("horas", texto(parametros, "horas"));
        }
        if (horas < 1 || horas > MAXIMO_HORAS) {
            throw new IllegalArgumentException("horas deve ficar entre 1 e " + MAXIMO_HORAS);
        }

        List<String> itens = new ArrayList<>();
        for (Registro registro : REGISTRO_DAO.listarUltimasHoras(horas)) {
            itens.add(registro.paraJson());
        }
        responder(troca, 200, Json.lista(itens));
    }

    // -----------------------------------------------------------------
    // POST /api/registros — o coração do projeto.
    // O robô (ou o ESP32) manda, no formato de formulário:
    //   nome_dispositivo=Ronaldo&localizacao=-23.58,-46.63&time_stamp=1791547200
    //   &temperatura=21.4&umidade=68.2&pressao=925.3&altitude=760
    //   &luminosidade=72.5&velocidade_vento=2.1&direcao_vento=135&intensidade_wifi=-58
    // (os nomes dos campos são os nomes das colunas)
    //
    // Passo a passo:
    //   1) lê e valida tudo ANTES de tocar no banco
    //   2) procura o dispositivo pelo nome
    //   3) se não existe: cadastra + cria LED1..LED4
    //      se existe e mandou coordenada: atualiza a localização
    //   4) grava a leitura em registros
    //   5) responde 201 (criado)
    // -----------------------------------------------------------------
    private static void cadastrarRegistro(HttpExchange troca) throws IOException, SQLException {
        Map<String, String> dados = lerFormulario(troca);

        // ---- 1) validação: nome e localização ----
        String nome = texto(dados, "nome_dispositivo");
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório: nome_dispositivo");
        }
        if (nome.length() > 30) {   // VARCHAR(30) no banco
            throw new IllegalArgumentException("nome_dispositivo passa de 30 caracteres");
        }
        String localizacao = texto(dados, "localizacao");   // opcional
        if (localizacao.length() > 50) {   // VARCHAR(50) no banco
            throw new IllegalArgumentException("localizacao passa de 50 caracteres");
        }

        // ---- 1) validação: time_stamp ----
        // Se não vier, o servidor usa a hora atual em segundos Unix.
        long timeStamp = System.currentTimeMillis() / 1000;
        if (!texto(dados, "time_stamp").isEmpty()) {
            timeStamp = converterLong("time_stamp", texto(dados, "time_stamp"));
        }
        if (timeStamp < 0 || timeStamp > 4294967295L) {   // limites do INT UNSIGNED
            throw new IllegalArgumentException("time_stamp fora do intervalo do INT UNSIGNED");
        }

        // ---- 1) validação: sensores ----
        // NOT NULL no banco → obrigatórios aqui
        float temperatura = numeroObrigatorio(dados, "temperatura");
        float umidade = numeroObrigatorio(dados, "umidade");
        float pressao = numeroObrigatorio(dados, "pressao");
        float altitude = numeroObrigatorio(dados, "altitude");
        float luminosidade = numeroObrigatorio(dados, "luminosidade");
        // aceitam NULL no banco → opcionais aqui (null se não vierem)
        Float velocidadeVento = numeroOpcional(dados, "velocidade_vento");
        Integer direcaoVento = inteiroOpcional(dados, "direcao_vento");
        Integer intensidadeWifi = inteiroOpcional(dados, "intensidade_wifi");
        // Sem limite de faixa (ex.: umidade 0–100): decisão de aula.
        // Dado estranho é marcado no dashboard, não recusado pelo servidor.

        // ---- 2) procura o dispositivo pelo nome ----
        Integer idDispositivo = DISPOSITIVO_DAO.buscarIdPorNome(nome);
        boolean dispositivoNovo = false;

        // ---- 3) cadastra se for novo; senão, atualiza a coordenada ----
        if (idDispositivo == null) {
            // localizacao vazia vira null → grava NULL, e não um texto vazio
            DISPOSITIVO_DAO.cadastrar(nome, localizacao.isEmpty() ? null : localizacao);
            // procura de novo para descobrir o id que o AUTO_INCREMENT deu
            // (o UNIQUE do nome garante que é ele)
            idDispositivo = DISPOSITIVO_DAO.buscarIdPorNome(nome);
            ATUADOR_DAO.criarLedsPadrao(idDispositivo);
            dispositivoNovo = true;
        } else if (!localizacao.isEmpty()) {
            DISPOSITIVO_DAO.atualizarLocalizacao(idDispositivo, localizacao);
        }

        // ---- 4) grava a leitura ----
        // id = 0 e dataHora = null: quem preenche é o MySQL
        Registro registro = new Registro(0, idDispositivo, timeStamp, null,
                temperatura, umidade, pressao, altitude, luminosidade,
                velocidadeVento, direcaoVento, intensidadeWifi);
        REGISTRO_DAO.cadastrar(registro);

        // ---- 5) responde ----
        // O robô usa id_dispositivo para achar os LEDs dele em /api/atuadores
        responder(troca, 201, "{\"mensagem\":\"Registro gravado\""
                + ",\"id_dispositivo\":" + idDispositivo
                + ",\"dispositivo_novo\":" + dispositivoNovo + "}");
    }


    // =================================================================
    //  3) /api/atuadores  — GET (ver os LEDs) e PATCH (ligar/desligar)
    //
    //  PATCH é o verbo HTTP para "alterar um pedaço" de algo que já existe.
    //  Aqui alteramos só o status de um LED; o resto da linha fica igual.
    // =================================================================
    private static void atenderAtuadores(HttpExchange troca) throws IOException {
        liberarAcesso(troca);
        try {
            String metodo = troca.getRequestMethod();
            if (metodo.equals("OPTIONS")) {
                responderSemCorpo(troca);
            } else if (metodo.equals("GET")) {
                listarAtuadores(troca);
            } else if (metodo.equals("PATCH")) {
                alterarAtuador(troca);
            } else {
                naoPermitido(troca, "GET, PATCH, OPTIONS");
            }
        } catch (IllegalArgumentException erro) {
            responder(troca, 400, "{\"erro\":" + Json.texto(erro.getMessage()) + "}");
        } catch (SQLException erro) {
            erro.printStackTrace();
            responder(troca, 500, "{\"erro\":\"erro ao acessar o banco\"}");
        }
    }

    private static void listarAtuadores(HttpExchange troca) throws IOException, SQLException {
        List<String> itens = new ArrayList<>();
        for (Atuador atuador : ATUADOR_DAO.listar()) {
            itens.add(atuador.paraJson());
        }
        responder(troca, 200, Json.lista(itens));
    }

    // PATCH /api/atuadores   corpo: id_atuador=2&status_atual=true
    private static void alterarAtuador(HttpExchange troca) throws IOException, SQLException {
        Map<String, String> dados = lerFormulario(troca);
        if (texto(dados, "id_atuador").isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório: id_atuador");
        }
        int idAtuador = converterInteiro("id_atuador", texto(dados, "id_atuador"));

        // aceita true/false (JavaScript) e 1/0 (como o MySQL guarda o BOOLEAN)
        String status = texto(dados, "status_atual");
        boolean ligado;
        if (status.equals("true") || status.equals("1")) {
            ligado = true;
        } else if (status.equals("false") || status.equals("0")) {
            ligado = false;
        } else {
            throw new IllegalArgumentException("status_atual deve ser true ou false");
        }

        int linhasAlteradas = ATUADOR_DAO.alterarStatus(idAtuador, ligado);
        if (linhasAlteradas == 0) {
            responder(troca, 404, "{\"erro\":\"Atuador não encontrado\"}");
            return;
        }
        responder(troca, 200, "{\"mensagem\":\"Atuador atualizado\"}");
    }


    // =================================================================
    //  4) /api/login  — só POST
    //  A página do robô manda a senha; o servidor compara e responde.
    //  Assim a senha nunca aparece no código da página.
    // =================================================================
    private static void atenderLogin(HttpExchange troca) throws IOException {
        liberarAcesso(troca);
        String metodo = troca.getRequestMethod();
        if (metodo.equals("OPTIONS")) {
            responderSemCorpo(troca);
        } else if (metodo.equals("POST")) {
            Map<String, String> dados = lerFormulario(troca);
            // equals() e não == : em Java, == compara se é o MESMO objeto,
            // equals() compara o CONTEÚDO do texto
            if (dados.getOrDefault("senha", "").equals(SENHA_ROBO)) {
                responder(troca, 200, "{\"mensagem\":\"Acesso liberado\"}");
            } else {
                responder(troca, 401, "{\"erro\":\"Senha incorreta\"}");
            }
        } else {
            naoPermitido(troca, "POST, OPTIONS");
        }
    }


    // =================================================================
    //  5) Páginas HTML  — tudo o que não começa com /api
    //  No Mural era uma página só (/public/index.html).
    //  Aqui são duas, uma em cada pasta, como o relatório pede no GitHub:
    //    src/main/resources/public/dashboard/index.html
    //    src/main/resources/public/IoT_Robot/index.html
    // =================================================================
    private static void abrirPaginas(HttpExchange troca) throws IOException {
        if (!troca.getRequestMethod().equals("GET")) {
            responder(troca, 405, "Método não permitido", "text/plain");
            return;
        }

        String caminho = troca.getRequestURI().getPath();
        String arquivo;
        if (caminho.equals("/") || caminho.equals("/dashboard")) {
            arquivo = "/public/dashboard/index.html";
        } else if (caminho.equals("/robo")) {
            arquivo = "/public/IoT_Robot/index.html";
        } else {
            responder(troca, 404, "Página não encontrada", "text/plain");
            return;
        }

        // getResourceAsStream lê do CLASSPATH (a pasta resources),
        // não do disco — igual ao Mural
        try (InputStream entrada = Main.class.getResourceAsStream(arquivo)) {
            if (entrada == null) {
                responder(troca, 404, "Página não encontrada", "text/plain");
                return;
            }
            byte[] pagina = entrada.readAllBytes();
            troca.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            troca.sendResponseHeaders(200, pagina.length);
            troca.getResponseBody().write(pagina);
            troca.close();
        }
    }


    // =================================================================
    //  FERRAMENTAS — métodos pequenos usados pelos atender...() acima
    // =================================================================

    // -----------------------------------------------------------------
    // CORS: as três linhas que no Mural ficavam no começo do atenderRecados.
    // Como agora são quatro rotas, viraram um método só.
    // Elas permitem abrir a página por outro endereço (ex.: o Live Server
    // do VS Code, na porta 5500) e mesmo assim chamar a API na 8080.
    // -----------------------------------------------------------------
    private static void liberarAcesso(HttpExchange troca) {
        troca.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        troca.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
        troca.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    // OPTIONS: o navegador pergunta "posso?" antes de um PATCH vindo de outro
    // endereço. Respondemos 204 (sem corpo) — os cabeçalhos CORS são a resposta.
    private static void responderSemCorpo(HttpExchange troca) throws IOException {
        troca.sendResponseHeaders(204, -1);
        troca.close();
    }

    private static void naoPermitido(HttpExchange troca, String metodosAceitos) throws IOException {
        troca.getResponseHeaders().set("Allow", metodosAceitos);
        responder(troca, 405, "{\"erro\":\"Método não permitido\"}");
    }

    // -----------------------------------------------------------------
    // Lê o CORPO da requisição (POST/PATCH) e decodifica.
    // Igual ao lerFormulario do Mural; a decodificação foi separada em
    // converterParametros() porque a query string (?horas=24) usa o
    // mesmo formato.
    // -----------------------------------------------------------------
    private static Map<String, String> lerFormulario(HttpExchange troca) throws IOException {
        String corpo = new String(
                troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8
        );
        return converterParametros(corpo);
    }

    // "autor=Tino&mensagem=Ol%C3%A1"  →  {autor=Tino, mensagem=Olá}
    private static Map<String, String> converterParametros(String texto) {
        Map<String, String> dados = new HashMap<>();
        if (texto == null || texto.isEmpty()) {   // sem ?... no endereço, chega null
            return dados;
        }
        for (String campo : texto.split("&")) {
            String[] partes = campo.split("=", 2);
            String nome = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length == 2
                    ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            dados.put(nome, valor);
        }
        return dados;
    }

    // Valor do campo sem espaços nas pontas; "" se o campo não veio
    private static String texto(Map<String, String> dados, String campo) {
        return dados.getOrDefault(campo, "").trim();
    }

    // -----------------------------------------------------------------
    // Conversões de texto → número.
    // Todas lançam IllegalArgumentException com uma mensagem clara;
    // os atender...() pegam essa exceção e respondem 400.
    // -----------------------------------------------------------------
    private static float numeroObrigatorio(Map<String, String> dados, String campo) {
        String valor = texto(dados, campo);
        if (valor.isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório: " + campo);
        }
        return converterFloat(campo, valor);
    }

    // Float (wrapper): devolve null quando o campo não veio
    private static Float numeroOpcional(Map<String, String> dados, String campo) {
        String valor = texto(dados, campo);
        if (valor.isEmpty()) {
            return null;
        }
        return converterFloat(campo, valor);
    }

    private static Integer inteiroOpcional(Map<String, String> dados, String campo) {
        String valor = texto(dados, campo);
        if (valor.isEmpty()) {
            return null;
        }
        return converterInteiro(campo, valor);
    }

    private static float converterFloat(String campo, String valor) {
        float numero;
        try {
            // aceita vírgula também: "21,4" vira "21.4"
            // (a mesma pegadinha do Scanner no Mac, lembra?)
            numero = Float.parseFloat(valor.replace(",", "."));
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Valor inválido em " + campo + ": " + valor);
        }
        // parseFloat aceita "NaN" e "Infinity", que não existem no MySQL nem no JSON
        if (Float.isNaN(numero) || Float.isInfinite(numero)) {
            throw new IllegalArgumentException("Valor inválido em " + campo + ": " + valor);
        }
        return numero;
    }

    private static int converterInteiro(String campo, String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Valor inválido em " + campo + ": " + valor);
        }
    }

    private static long converterLong(String campo, String valor) {
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Valor inválido em " + campo + ": " + valor);
        }
    }

    // -----------------------------------------------------------------
    // responder(): idêntico ao do Mural. Duas versões (sobrecarga):
    // a de 3 parâmetros assume JSON; a de 4 deixa escolher o tipo.
    // -----------------------------------------------------------------
    private static void responder(HttpExchange troca, int status, String conteudo) throws IOException {
        responder(troca, status, conteudo, "application/json");
    }

    private static void responder(HttpExchange troca, int status, String conteudo, String tipo) throws IOException {
        byte[] resposta = conteudo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", tipo + "; charset=UTF-8");
        troca.sendResponseHeaders(status, resposta.length);
        troca.getResponseBody().write(resposta);
        troca.close();
    }
}
