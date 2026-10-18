package tech.cameia.cuentas.presentation;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente HTTP de pruebas que escribe la petición byte a byte en un socket. Existe porque
 * {@code java.net.URI} rechaza {@code %zz} y {@code TestRestTemplate} normaliza los encabezados:
 * aquí la petición mala llega tal cual al servidor.
 */
final class RawHttpClient {

    private static final String CRLF = "\r\n";
    private static final String HEADERS_END = CRLF + CRLF;

    /**
     * Respuesta reducida a lo que las pruebas comprueban.
     *
     * @param status  código de estado de la línea de respuesta
     * @param headers encabezados con el nombre en minúsculas
     * @param body    cuerpo ya decodificado (sin fragmentos) y convertido a UTF-8
     */
    record Reply(int status, Map<String, String> headers, String body) {
    }

    private final int port;

    RawHttpClient(int port) {
        this.port = port;
    }

    /**
     * Envía una petición sin validarla ni normalizarla.
     *
     * @param method  método HTTP, por ejemplo {@code PATCH}
     * @param target  ruta y consulta tal como se escriben en la línea de petición, sin validar
     * @param headers encabezados completos {@code Nombre: valor}; se escriben en ISO-8859-1
     * @param body    cuerpo (cada carácter es un byte, ISO-8859-1) o {@code null}
     * @return la respuesta leída hasta el cierre de la conexión
     * @throws IOException si el socket falla
     */
    Reply send(String method, String target, List<String> headers, String body) throws IOException {
        byte[] payload = body == null ? new byte[0] : body.getBytes(ISO_8859_1);
        StringBuilder head = new StringBuilder()
                .append(method).append(' ').append(target).append(" HTTP/1.1").append(CRLF)
                .append("Host: localhost").append(CRLF)
                .append("Connection: close").append(CRLF);
        for (String header : headers) {
            head.append(header).append(CRLF);
        }
        if (body != null) {
            head.append("Content-Length: ").append(payload.length).append(CRLF);
        }
        head.append(CRLF);
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(15_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.toString().getBytes(ISO_8859_1));
            out.write(payload);
            out.flush();
            return parse(readAll(socket.getInputStream()));
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        // ISO-8859-1 conserva un carácter por byte: los largos de los fragmentos son en bytes.
        return buffer.toString(ISO_8859_1);
    }

    private static Reply parse(String raw) {
        int split = raw.indexOf(HEADERS_END);
        String head = split < 0 ? raw : raw.substring(0, split);
        String rawBody = split < 0 ? "" : raw.substring(split + HEADERS_END.length());
        String[] lines = head.split(CRLF);
        int status = Integer.parseInt(lines[0].split(" ")[1]);
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(colon + 1).trim());
            }
        }
        String decoded = "chunked".equalsIgnoreCase(headers.get("transfer-encoding")) ? dechunk(rawBody) : rawBody;
        return new Reply(status, headers, new String(decoded.getBytes(ISO_8859_1), UTF_8));
    }

    private static String dechunk(String chunked) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (pos < chunked.length()) {
            int lineEnd = chunked.indexOf(CRLF, pos);
            if (lineEnd < 0) {
                break;
            }
            String sizeText = chunked.substring(pos, lineEnd);
            int semicolon = sizeText.indexOf(';');
            int size = Integer.parseInt((semicolon < 0 ? sizeText : sizeText.substring(0, semicolon)).trim(), 16);
            if (size == 0) {
                break;
            }
            int start = lineEnd + CRLF.length();
            out.append(chunked, start, start + size);
            pos = start + size + CRLF.length();
        }
        return out.toString();
    }
}
