package com.nogomy.net;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RouterOsApi {

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public void connect(String host, int port, String user, String pass) throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(7000);

        in = socket.getInputStream();
        out = socket.getOutputStream();

        List<String> loginWords = new ArrayList<>();
        loginWords.add("/login");
        loginWords.add("=name=" + user);
        loginWords.add("=password=" + pass);

        writeSentence(loginWords);

        List<String> reply = readReply();

        boolean done = reply.contains("!done");

        if (!done) {
            throw new IOException("فشل تسجيل الدخول إلى MikroTik");
        }
    }

    public List<Map<String, String>> command(
            String command,
            String... words) throws Exception {

        List<String> sentence = new ArrayList<>();
        sentence.add(command);
        Collections.addAll(sentence, words);

        writeSentence(sentence);

        return readMaps();
    }

    public List<Map<String, String>> command(
            String command,
            Map<String, String> attrs) throws Exception {

        List<String> sentence = new ArrayList<>();
        sentence.add(command);

        for (Map.Entry<String, String> e : attrs.entrySet()) {
            sentence.add("=" + e.getKey() + "=" + e.getValue());
        }

        writeSentence(sentence);

        return readMaps();
    }

    private void writeSentence(List<String> words) throws IOException {
        for (String w : words) {
            writeWord(w);
        }

        out.write(0);
        out.flush();
    }

    private void writeWord(String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int n = b.length;

        if (n < 0x80) {
            out.write(n);

        } else if (n < 0x4000) {
            out.write((n >> 8) | 0x80);
            out.write(n & 0xff);

        } else if (n < 0x200000) {
            out.write((n >> 16) | 0xC0);
            out.write((n >> 8) & 0xff);
            out.write(n & 0xff);

        } else if (n < 0x10000000) {
            out.write((n >> 24) | 0xE0);
            out.write((n >> 16) & 0xff);
            out.write((n >> 8) & 0xff);
            out.write(n & 0xff);

        } else {
            out.write(0xF0);
            out.write((n >> 24) & 0xff);
            out.write((n >> 16) & 0xff);
            out.write((n >> 8) & 0xff);
            out.write(n & 0xff);
        }

        out.write(b);
    }

    private int readLen() throws IOException {
        int c = in.read();

        if (c < 0) {
            throw new EOFException();
        }

        if (c < 0x80) {
            return c;
        }

        if (c < 0xC0) {
            int b1 = in.read();

            if (b1 < 0) {
                throw new EOFException();
            }

            return ((c & 0x3f) << 8) | b1;
        }

        if (c < 0xE0) {
            int b1 = in.read();
            int b2 = in.read();

            if (b1 < 0 || b2 < 0) {
                throw new EOFException();
            }

            return ((c & 0x1f) << 16)
                    | (b1 << 8)
                    | b2;
        }

        if (c < 0xF0) {
            int b1 = in.read();
            int b2 = in.read();
            int b3 = in.read();

            if (b1 < 0 || b2 < 0 || b3 < 0) {
                throw new EOFException();
            }

            return ((c & 0x0f) << 24)
                    | (b1 << 16)
                    | (b2 << 8)
                    | b3;
        }

        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();

        if (b1 < 0 || b2 < 0 || b3 < 0 || b4 < 0) {
            throw new EOFException();
        }

        return (b1 << 24)
                | (b2 << 16)
                | (b3 << 8)
                | b4;
    }

    private String readWord() throws IOException {
        int n = readLen();

        byte[] b = new byte[n];
        int p = 0;

        while (p < n) {
            int r = in.read(b, p, n - p);

            if (r < 0) {
                throw new EOFException();
            }

            p += r;
        }

        return new String(b, StandardCharsets.UTF_8);
    }

    private List<String> readReply() throws IOException {
        List<String> result = new ArrayList<>();

        while (true) {
            String w = readWord();

            result.add(w);

            if (w.equals("!done")
                    || w.equals("!fatal")
                    || w.equals("!trap")) {
                break;
            }
        }

        return result;
    }

    private List<Map<String, String>> readMaps() throws IOException {
        List<Map<String, String>> result = new ArrayList<>();

        Map<String, String> current = null;

        while (true) {
            String w = readWord();

            if (w.equals("!re")) {
                current = new LinkedHashMap<>();
                result.add(current);
                continue;
            }

            if (w.equals("!done")
                    || w.equals("!fatal")
                    || w.equals("!trap")) {
                break;
            }

            if (w.startsWith("=") && current != null) {
                int p = w.indexOf('=', 1);

                if (p > 0) {
                    current.put(
                            w.substring(1, p),
                            w.substring(p + 1)
                    );
                }
            }
        }

        return result;
    }

    public Map<String, String> getResource() throws Exception {
        List<Map<String, String>> r =
                command("/system/resource/print");

        return r.isEmpty()
                ? new HashMap<>()
                : r.get(0);
    }

    public List<Map<String, String>> getHotspotUsers()
            throws Exception {

        return command("/ip/hotspot/user/print");
    }

    public List<Map<String, String>> getHotspotActive()
            throws Exception {

        return command("/ip/hotspot/active/print");
    }

    /*
     * إضافة مستخدم Hotspot إلى MikroTik
     *
     * email:
     * مثال للصلاحية يوم:
     * 1@nobind.com
     *
     * مثال للصلاحية ساعة:
     * 1h@nobind.com
     */
    public void addHotspotUser(
            String name,
            String password,
            String profile,
            String limitUptime,
            String email) throws Exception {

        Map<String, String> attrs = new LinkedHashMap<>();

        attrs.put("name", name);
        attrs.put("password", password);
        attrs.put("profile", profile);

        if (limitUptime != null && !limitUptime.isEmpty()) {
            attrs.put("limit-uptime", limitUptime);
        }

        if (email != null && !email.isEmpty()) {
            attrs.put("email", email);
        }

        command("/ip/hotspot/user/add", attrs);
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }
}
