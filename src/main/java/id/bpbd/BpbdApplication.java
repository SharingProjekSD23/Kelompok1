
package id.bpbd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@SpringBootApplication
public class BpbdApplication {
    public static void main(String[] args) {
        SpringApplication.run(BpbdApplication.class, args);
    }
}

@Controller
class BpbdController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    // ---------- KONEKSI SUPABASE ----------

    private JsonNode ambilData(String tabel) throws Exception {
        cekKonfigurasi();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        supabaseUrl.replaceAll("/+$", "")
                        + "/rest/v1/" + tabel + "?select=*"))
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Supabase gagal membaca tabel " + tabel
                    + ". HTTP " + response.statusCode()
                    + ": " + response.body());
        }

        return mapper.readTree(response.body());
    }

    private void simpanData(String tabel, Map<String, Object> data)
            throws Exception {
        cekKonfigurasi();

        String json = mapper.writeValueAsString(data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        supabaseUrl.replaceAll("/+$", "")
                        + "/rest/v1/" + tabel))
                .header("apikey", supabaseKey)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Gagal menyimpan data. HTTP "
                    + response.statusCode() + ": " + response.body());
        }
    }

    private void cekKonfigurasi() {
        if (supabaseUrl == null || supabaseUrl.isBlank()
                || supabaseKey == null || supabaseKey.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_URL dan SUPABASE_KEY belum diatur.");
        }
    }

    private int jumlah(String tabel) {
        try {
            return ambilData(tabel).size();
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------- TAMPILAN UTAMA ----------

    private String halaman(String judul, String isi) {
        String html = """
        <!DOCTYPE html>
        <html lang="id">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>__JUDUL__ | BPBD Command Center</title>
          <style>
            :root{--orange:#f15a24;--orange-deep:#c83e12;--orange-soft:#fff0e8;--navy:#142b45;--ink:#172b40;--muted:#6b7c8f;--line:#e7edf2;--canvas:#f4f7fa;--shadow:0 16px 42px rgba(22,43,65,.07)}
            *{box-sizing:border-box}html{scroll-behavior:smooth}
            body{margin:0;color:var(--ink);font-family:"Plus Jakarta Sans","Inter","Segoe UI",Arial,sans-serif;font-size:14px;line-height:1.55;background:radial-gradient(ellipse at 8% 0%,rgba(241,90,36,.09),transparent 28%),radial-gradient(ellipse at 100% 20%,rgba(32,62,93,.08),transparent 30%),var(--canvas);min-height:100vh}
            .main{margin:0 auto;padding:30px 34px 24px;width:100%;max-width:1640px}
            .topbar{display:flex;justify-content:space-between;align-items:center;gap:24px;margin:0 0 25px;padding:31px 34px;min-height:190px;border-radius:24px;color:#fff;background:linear-gradient(118deg,#142b45 0%,#203e5d 48%,#f15a24 100%);box-shadow:0 18px 38px rgba(20,43,69,.18);position:relative;overflow:hidden;isolation:isolate}
            .topbar:before{content:"";position:absolute;z-index:-1;width:390px;height:390px;border:1px solid rgba(255,255,255,.14);border-radius:50%;right:13%;top:-265px;box-shadow:0 0 0 35px rgba(255,255,255,.035),0 0 0 75px rgba(255,255,255,.025)}
            .topbar:after{content:"";position:absolute;z-index:-1;width:180px;height:180px;border-radius:40px;transform:rotate(25deg);right:-45px;bottom:-110px;background:rgba(255,255,255,.08)}
            .hero-copy{max-width:690px}.eyebrow{display:inline-flex;align-items:center;gap:9px;color:#ffe4d6;font-size:11px;font-weight:800;letter-spacing:1.7px;text-transform:uppercase}.eyebrow:before{content:"";width:25px;height:3px;border-radius:8px;background:#ffb27d}
            h1{font-size:clamp(28px,3vw,38px);line-height:1.15;margin:12px 0 10px;letter-spacing:-1.1px;font-weight:800}h2{font-size:18px;line-height:1.3;margin:0 0 5px;letter-spacing:-.35px;font-weight:800;color:var(--navy)}h3{font-size:15px;margin:0;font-weight:800}
            .hero-subtitle{color:rgba(255,255,255,.82);font-size:14px;margin:0;max-width:560px}.top-actions{display:flex;align-items:center;gap:12px;position:relative;z-index:1;flex-shrink:0}
            .date{background:rgba(255,255,255,.12);color:#fff;border:1px solid rgba(255,255,255,.25);border-radius:12px;padding:11px 14px;font-size:12px;font-weight:700;white-space:nowrap;backdrop-filter:blur(8px)}
            .button,button{display:inline-flex;align-items:center;justify-content:center;gap:9px;border:0;border-radius:12px;padding:12px 17px;background:linear-gradient(135deg,#f15a24,#ff7b35);color:#fff;font-weight:800;text-decoration:none;cursor:pointer;font-size:13px;line-height:1.2;box-shadow:0 8px 18px rgba(241,90,36,.22);transition:transform .18s,box-shadow .18s,filter .18s}
            .button:hover,button:hover{transform:translateY(-2px);filter:saturate(1.08);box-shadow:0 12px 24px rgba(241,90,36,.28)}
            .transaction-cta{min-height:68px;padding:10px 18px 10px 12px;border-radius:17px;color:#fff;background:linear-gradient(135deg,#ff7b35,#ed4c18);box-shadow:0 12px 26px rgba(8,22,39,.23);border:1px solid rgba(255,255,255,.2)}
            .transaction-cta:hover{background:linear-gradient(135deg,#ff8b4c,#db4314)}
            .plus-mark{display:grid;place-items:center;width:46px;height:46px;border-radius:12px;background:#fff;color:var(--orange-deep);font-size:31px;font-weight:500;line-height:1;box-shadow:inset 0 0 0 1px rgba(255,255,255,.55)}
            .cta-label{display:flex;flex-direction:column;align-items:flex-start;gap:2px}.cta-label strong{font-size:13px;letter-spacing:.1px}.cta-label small{font-size:10px;font-weight:600;color:#fff0e8}
            .section-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin:28px 2px 14px}.section-heading p{margin:5px 0 0;color:var(--muted);font-size:12px}
            .section-kicker{font-size:10px;text-transform:uppercase;letter-spacing:1.35px;color:var(--orange-deep);font-weight:900;margin-bottom:6px}
            .grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin:0 0 25px}
            .card,.panel{background:rgba(255,255,255,.94);border:1px solid rgba(221,230,237,.9);border-radius:18px;padding:21px;box-shadow:var(--shadow)}
            .card{position:relative;overflow:hidden;min-height:150px}.card:after{content:"";position:absolute;width:100px;height:100px;border-radius:50%;right:-39px;top:-40px;background:rgba(241,90,36,.07)}.card:before{content:"";position:absolute;left:0;top:20px;bottom:20px;width:4px;border-radius:0 5px 5px 0;background:linear-gradient(#ff9b62,#f15a24)}
            .metric-top{display:flex;align-items:center;justify-content:space-between;gap:12px}.metric-icon{display:grid;place-items:center;width:43px;height:43px;border-radius:13px;background:var(--orange-soft);color:var(--orange-deep);font-size:21px;font-weight:800}
            .metric-label{color:var(--muted);font-size:12px;font-weight:700;margin-top:16px}.metric-value{font-size:32px;line-height:1.15;font-weight:850;margin-top:5px;color:var(--navy);letter-spacing:-.8px}.metric-foot{font-size:11px;color:#8290a0;margin-top:8px}
            .panel{margin:0 0 22px;padding:23px 24px;overflow:hidden}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:18px}.panel-subtitle{color:var(--muted);font-size:12px;margin-top:5px}
            .soft-pill{display:inline-flex;align-items:center;gap:7px;border-radius:99px;background:#f0f5f8;color:#50657a;padding:7px 10px;font-size:10px;font-weight:800;white-space:nowrap}.soft-pill:before{content:"";width:6px;height:6px;border-radius:50%;background:#f15a24}
            .category-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:13px}.category-card{position:relative;border:1px solid var(--line);border-radius:15px;padding:17px;background:linear-gradient(145deg,#fff 0%,#fbfcfd 100%);overflow:hidden}.category-card:after{content:"";position:absolute;width:70px;height:70px;right:-34px;bottom:-36px;border:13px solid rgba(241,90,36,.07);border-radius:50%}
            .category-name{font-weight:800;font-size:14px;color:var(--navy);overflow-wrap:anywhere}.category-count{font-size:25px;font-weight:850;line-height:1.2;color:var(--orange-deep);margin-top:13px}.category-meta{font-size:11px;color:var(--muted);margin-top:4px}.category-bar{height:4px;border-radius:99px;background:#edf1f4;margin-top:14px;overflow:hidden}.category-bar span{display:block;height:100%;border-radius:99px;background:linear-gradient(90deg,#f15a24,#ffb16e)}
            .table-wrap{width:100%;overflow-x:auto;border:1px solid var(--line);border-radius:13px}table{border-collapse:separate;border-spacing:0;width:100%;font-size:12px;min-width:580px;background:#fff}th,td{padding:14px 15px;border-bottom:1px solid #edf1f4;text-align:left;white-space:nowrap}th{color:#65768a;background:#f5f8fa;font-size:10px;font-weight:900;letter-spacing:.65px;text-transform:uppercase}tbody tr:last-child td{border-bottom:0}tbody tr:hover{background:#fffaf7}
            .status-in,.status-out{display:inline-flex;align-items:center;gap:6px;padding:6px 9px;border-radius:99px;font-size:10px;font-weight:900}.status-in{background:#e8f7ef;color:#16764e}.status-out{background:#fff0e8;color:#c3471b}.empty-state{padding:28px;text-align:center;color:var(--muted);font-size:13px}
            .muted{color:var(--muted);font-size:13px}.footer{display:flex;justify-content:space-between;gap:12px;color:#8190a0;font-size:11px;padding:15px 2px 8px;border-top:1px solid #e4eaf0;margin-top:24px}
            .notice{padding:15px 17px;background:linear-gradient(135deg,#fff0e5,#fff8f2);border:1px solid #ffe0ca;border-radius:12px;color:#b74400;margin:18px 0;font-size:13px}
            input,select,textarea{width:100%;border:1px solid #dce4eb;border-radius:10px;padding:12px 13px;margin:7px 0 17px;font:inherit;background:#fbfcfd;color:var(--ink)}input:focus,select:focus,textarea:focus{outline:3px solid rgba(241,90,36,.15);border-color:#f15a24}label{font-size:12px;font-weight:800;color:#334b62}textarea{min-height:95px;resize:vertical}
            @media(max-width:1100px){.main{padding:22px}.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.category-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.topbar{padding:27px}}
            @media(max-width:700px){.main{padding:14px}.topbar{padding:22px;align-items:flex-start;flex-direction:column;border-radius:19px;min-height:0}.top-actions{width:100%;justify-content:space-between;flex-wrap:wrap}.date{padding:10px 12px}.transaction-cta{min-height:62px}.grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.card{padding:15px;min-height:138px}.metric-value{font-size:27px}.metric-label{font-size:11px}.category-grid{grid-template-columns:1fr}.panel{padding:17px 14px;border-radius:15px}.panel-head,.section-heading{align-items:flex-start;flex-direction:column}h1{font-size:28px}.footer{flex-direction:column}}
            @media(max-width:390px){.grid{grid-template-columns:1fr}.transaction-cta{width:100%}}

            /* Dashboard readability and data visualization */
            body { font-size:16px; line-height:1.6; }
            .main { max-width:1720px; padding:32px 38px 28px; }
            h1 { font-size:clamp(32px,3vw,42px); }
            h2 { font-size:21px; }
            .hero-subtitle { font-size:16px; line-height:1.65; }
            .eyebrow { font-size:12px; }
            .metric-label { font-size:14px; }
            .metric-value { font-size:36px; }
            .metric-foot,.panel-subtitle,.section-heading p { font-size:13px; }
            .panel { padding:26px 28px; margin-bottom:24px; }
            .panel-head { margin-bottom:22px; }
            .table-wrap { overflow-x:auto; border:1px solid var(--line); border-radius:14px; }
            table { font-size:14px; min-width:720px; }
            th { font-size:12px; padding:15px 16px; }
            td { font-size:14px; padding:15px 16px; }
            .transaction-cta { min-height:68px; }
            .plus-mark { width:48px;height:48px;font-size:34px; }
            .form-shell { max-width:1080px;margin:0 auto; }
            .form-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px; }
            .form-field.full { grid-column:1/-1; }
            .form-section { background:#f8fafc;border:1px solid #e5ebf0;border-radius:15px;padding:20px;margin:18px 0; }
            .form-section h3 { color:var(--navy);font-size:16px;margin-bottom:4px; }
            .form-section p { color:var(--muted);font-size:13px;margin:0 0 15px; }
            .item-row { display:grid;grid-template-columns:minmax(0,2fr) minmax(120px,1fr) 44px;gap:12px;align-items:end;padding:14px;background:#fff;border:1px solid #e5ebf0;border-radius:13px;margin:10px 0; }
            .item-row label { display:block; }
            .item-row select,.item-row input { margin-bottom:0; }
            .remove-item { height:44px;padding:0;background:#fff0e8;color:#b74400;box-shadow:none;border:1px solid #ffd6c1;font-size:20px; }
            .chart-grid { display:grid;grid-template-columns:1.25fr 1fr 1fr;gap:18px;margin-bottom:24px; }
            .chart-panel { min-width:0;background:#fff;border:1px solid #e1e8ef;border-radius:18px;padding:22px;box-shadow:var(--shadow); }
            .chart-panel h3 { font-size:16px;color:var(--navy);margin-bottom:4px; }
            .chart-panel .chart-desc { color:var(--muted);font-size:12px;margin-bottom:18px; }
            .chart-canvas { width:100%;min-height:190px; }
            .chart-canvas svg { width:100%;height:auto;display:block; }
            .chart-legend { display:flex;flex-wrap:wrap;gap:10px 14px;margin-top:14px;font-size:12px;color:#53667a; }
            .legend-dot { display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:6px; }
            .choice-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px; }
            .choice-card { text-decoration:none;color:var(--ink);display:block;padding:28px;border:1px solid #e1e8ef;border-radius:20px;background:#fff;box-shadow:var(--shadow);transition:transform .18s,box-shadow .18s; }
            .choice-card:hover { transform:translateY(-3px);box-shadow:0 18px 38px rgba(22,43,65,.12); }
            .choice-icon { display:grid;place-items:center;width:58px;height:58px;border-radius:17px;font-size:30px;font-weight:800;margin-bottom:18px; }
            .choice-in .choice-icon { background:#e6f7ef;color:#14764d; }
            .choice-out .choice-icon { background:#fff0e8;color:#c3471b; }
            .choice-card h2 { margin-bottom:8px; }
            .choice-card p { margin:0;color:var(--muted);font-size:14px; }
            .form-title-row { display:flex;align-items:center;gap:14px;margin-bottom:20px; }
            .form-title-icon { display:grid;place-items:center;width:54px;height:54px;border-radius:16px;font-size:28px;font-weight:800; }
            .form-title-icon.in { background:#e6f7ef;color:#14764d; }
            .form-title-icon.out { background:#fff0e8;color:#c3471b; }
            .form-actions { display:flex;gap:12px;flex-wrap:wrap;margin-top:22px; }
            @media(max-width:1200px) { .chart-grid { grid-template-columns:1fr 1fr; } .chart-panel:first-child { grid-column:1/-1; } }
            @media(max-width:760px) {
              .main { padding:16px; }
              .panel { padding:19px 16px; }
              .chart-grid,.choice-grid,.form-grid { grid-template-columns:1fr; }
              .chart-panel:first-child { grid-column:auto; }
              .item-row { grid-template-columns:minmax(0,1fr) 100px 42px;gap:8px;padding:10px; }
              .item-row select,.item-row input { padding:10px 8px; }
              .form-field.full { grid-column:auto; }
              h2 { font-size:19px; }
              table { min-width:680px; }
            }

          </style>
        </head>
        <body>
          <main class="main">
            <header class="topbar">
              <div class="hero-copy">
                <div class="eyebrow">BPBD KOTA SEMARANG · PUSAT LOGISTIK</div>
                <h1>__JUDUL_H1__</h1>
                <p class="hero-subtitle">Pantau ketersediaan bantuan dan catat pergerakan barang dalam satu ruang kerja yang terintegrasi.</p>
              </div>
              <div class="top-actions">
                <div class="date">▦ &nbsp; __TANGGAL__</div>
                <a class="button transaction-cta" href="/input-transaksi" aria-label="Tambah transaksi logistik" title="Tambah transaksi logistik">
                  <span class="plus-mark">+</span>
                  <span class="cta-label"><strong>Transaksi Logistik</strong><small>Catat barang masuk / keluar</small></span>
                </a>
              </div>
            </header>
            __ISI__
            <div class="footer">
              <span>BPBD Kota Semarang · Sistem Logistik</span>
              <span>Dashboard operasional persediaan</span>
            </div>
          </main>
        </body>
        </html>
        """;
        return html.replace("__JUDUL__", escape(judul))
                .replace("__JUDUL_H1__", escape(judul))
                .replace("__TANGGAL__", LocalDate.now().toString())
                .replace("__ISI__", isi == null ? "" : isi);
    }

    private String kartu(String ikon, String label, int nilai) {
        String catatan = switch (label) {
            case "Jenis Barang" -> "Tipe barang terdaftar";
            case "Estimasi Stok" -> "Akumulasi masuk dikurangi keluar";
            case "Total Barang Masuk" -> "Jumlah unit tercatat masuk";
            case "Total Barang Keluar" -> "Jumlah unit tercatat keluar";
            default -> "Ringkasan data logistik";
        };
        return """
        <div class="card">
          <div class="metric-top"><div class="metric-icon">%s</div><span class="soft-pill">Terdata</span></div>
          <div class="metric-label">%s</div>
          <div class="metric-value">%s</div>
          <div class="metric-foot">%s</div>
        </div>
        """.formatted(escape(ikon), escape(label), nilai, escape(catatan));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String pesan(String teks) {
        return "<div class='notice'>" + escape(teks) + "</div>"
                + "<p><a class='button' href='/'>Kembali ke Dashboard</a></p>";
    }

    // ---------- DASHBOARD UTAMA ----------

    @GetMapping("/")
    @ResponseBody
    public String dashboard() {
        // Halaman utama diarahkan ke dashboard yang berfokus pada logistik.
        return logistik();
    }

    // ---------- DASHBOARD LOGISTIK ----------

    @GetMapping("/logistik")
    @ResponseBody
    public String logistik() {
        StringBuilder isi = new StringBuilder();
        try {
            JsonNode masterBarangAsli = ambilData("master_barang");
            JsonNode transaksi = ambilData("logistik_transaksi");
            JsonNode barang = masterBarangUnik(masterBarangAsli);
            Map<Integer, Long> stokPerBarang = stokTerkonsolidasi(masterBarangAsli, barang, transaksi);
            long masuk = 0;
            long keluar = 0;
            for (JsonNode row : transaksi) {
                long jumlah = angkaPertama(row, "jumlah", "kuantitas", "qty");
                String jenisTransaksi = jenisTransaksi(row);
                if ("Masuk".equals(jenisTransaksi)) masuk += jumlah;
                if ("Keluar".equals(jenisTransaksi)) keluar += jumlah;
            }

            isi.append("<div class='section-heading' style='margin-top:0'><div>")
                .append("<div class='section-kicker'>Ringkasan operasional</div>")
                .append("<h2>Ikhtisar Logistik</h2><p>Pantau persediaan dan pergerakan bantuan secara ringkas.</p>")
                .append("</div><span class='soft-pill'>Data terhubung</span></div>");

            isi.append("<div class='grid'>")
                .append(kartu("▦", "Jenis Barang", barang.size()))
                .append(kartu("▣", "Estimasi Stok", (int) stokPerBarang.values().stream().mapToLong(Long::longValue).sum()))
                .append(kartu("↓", "Total Barang Masuk", (int) masuk))
                .append(kartu("↑", "Total Barang Keluar", (int) keluar))
                .append("</div>");

            isi.append("<div class='section-heading'><div><div class='section-kicker'>Analisis pergerakan</div>")
                .append("<h2>Insight Logistik</h2><p>Tren transaksi, volume barang, dan komposisi kategori persediaan.</p></div></div>")
                .append("<div class='chart-grid'>")
                .append(chartTren(transaksi))
                .append(chartBatang(barang, stokPerBarang))
                .append(chartPie(barang))
                .append("</div>");

            isi.append("<section class='panel'><div class='panel-head'><div>")
                .append("<div class='section-kicker'>Daftar inventaris</div>")
                .append("<h2>Ringkasan Persediaan</h2>")
                .append("<div class='panel-subtitle'>Rincian barang terdaftar beserta kategori, satuan, estimasi stok, dan pemantauan kedaluwarsa.</div>")
                .append("</div><span class='soft-pill'>").append(barang.size()).append(" jenis barang</span></div>")
                .append(tabelPersediaan(barang, stokPerBarang))
                .append("</section>");

            isi.append("<section class='panel'><div class='panel-head'><div>")
                .append("<div class='section-kicker'>Aktivitas terbaru</div>")
                .append("<h2>Log Transaksi Terakhir</h2>")
                .append("<div class='panel-subtitle'>Enam transaksi terbaru berdasarkan tanggal.</div>")
                .append("</div><a class='button transaction-cta' href='/input-transaksi'><span class='plus-mark'>+</span>")
                .append("<span class='cta-label'><strong>Transaksi Baru</strong><small>Barang masuk / keluar</small></span></a></div>")
                .append(tabelTransaksiTerbaru(transaksi))
                .append("</section>");
        } catch (Exception e) {
            isi.append(pesan(e.getMessage()));
        }
        return halaman("Dashboard Logistik", isi.toString());
    }

    private String tabelTransaksiTerbaru(JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return "<div class='empty-state'>Belum ada transaksi logistik yang tercatat.</div>";
        }

        List<JsonNode> data = new ArrayList<>();
        rows.forEach(data::add);
        data.sort((a, b) -> b.path("tanggal").asText("")
                .compareTo(a.path("tanggal").asText("")));

        StringBuilder html = new StringBuilder("""
            <div class='table-wrap'><table>
            <thead><tr>
              <th>Tanggal</th><th>Jenis</th><th>Barang ID</th><th>Jumlah</th>
              <th>Kecamatan</th><th>Sumber / Tujuan</th><th>Keterangan</th>
            </tr></thead><tbody>
            """);

        int batas = Math.min(6, data.size());
        for (int i = 0; i < batas; i++) {
            JsonNode row = data.get(i);
            String jenis = jenisTransaksi(row);
            boolean masuk = "Masuk".equals(jenis);
            String sumberTujuan = masuk
                    ? row.path("sumber_barang").asText("-")
                    : row.path("tujuan_penggunaan").asText("-");
            String barangId = row.path("barang_id").asText(row.path("id_barang").asText("-"));
            String jumlah = row.path("jumlah").asText(row.path("kuantitas").asText(row.path("qty").asText("0")));

            html.append("<tr><td>").append(escape(row.path("tanggal").asText("-")))
                .append("</td><td>")
                .append(masuk ? "<span class='status-in'>↓ Barang Masuk</span>"
                              : "<span class='status-out'>↑ Barang Keluar</span>")
                .append("</td><td>").append(escape(barangId))
                .append("</td><td><strong>").append(escape(jumlah))
                .append("</strong></td><td>").append(escape(row.path("kecamatan").asText("-")))
                .append("</td><td>").append(escape(sumberTujuan))
                .append("</td><td>").append(escape(row.path("keterangan").asText("-")))
                .append("</td></tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    // Menggunakan kombinasi nama + kategori + satuan sebagai identitas logis
    // untuk menghindari master duplikat tampil dua kali. ID terkecil dipertahankan
    // sebagai ID kanonis untuk dropdown; transaksi lama tetap ikut dihitung.
    private String kunciBarang(JsonNode row) {
        return row.path("nama_barang").asText(row.path("nama").asText(""))
                    .trim().toLowerCase(Locale.ROOT) + "|"
             + row.path("kategori").asText("").trim().toLowerCase(Locale.ROOT) + "|"
             + row.path("satuan").asText("").trim().toLowerCase(Locale.ROOT);
    }

    private int idBarang(JsonNode row) {
        return (int) angkaPertama(row, "barang_id", "id", "id_barang");
    }

    private JsonNode masterBarangUnik(JsonNode masterAsli) {
        com.fasterxml.jackson.databind.node.ArrayNode hasil = mapper.createArrayNode();
        if (masterAsli == null || !masterAsli.isArray()) return hasil;

        Map<String, JsonNode> unik = new TreeMap<>();
        for (JsonNode row : masterAsli) {
            String key = kunciBarang(row);
            JsonNode sebelumnya = unik.get(key);
            if (sebelumnya == null || idBarang(row) < idBarang(sebelumnya)) {
                unik.put(key, row);
            }
        }
        unik.values().forEach(hasil::add);
        return hasil;
    }

    private Map<Integer, Long> stokTerkonsolidasi(
            JsonNode masterAsli, JsonNode masterUnik, JsonNode transaksi) {
        Map<Integer, Long> stokPerIdAsli = hitungStokPerBarang(masterAsli, transaksi);
        Map<String, Integer> idKanonis = new HashMap<>();
        if (masterUnik != null && masterUnik.isArray()) {
            for (JsonNode row : masterUnik) idKanonis.put(kunciBarang(row), idBarang(row));
        }

        Map<Integer, Long> hasil = new HashMap<>();
        if (masterAsli != null && masterAsli.isArray()) {
            for (JsonNode row : masterAsli) {
                Integer kanonis = idKanonis.get(kunciBarang(row));
                if (kanonis == null) continue;
                hasil.merge(kanonis, stokPerIdAsli.getOrDefault(idBarang(row), 0L), Long::sum);
            }
        }
        return hasil;
    }

    private Map<Integer, Long> hitungStokPerBarang(JsonNode barang, JsonNode transaksi) {
        Map<Integer, Long> stok = new HashMap<>();

        // Jadikan stok yang tersimpan pada master barang sebagai saldo awal.
        if (barang != null && barang.isArray()) {
            for (JsonNode row : barang) {
                int id = (int) angkaPertama(row, "id", "barang_id", "id_barang");
                if (id <= 0) continue;
                long saldoAwal = angkaPertama(row, "stok_awal", "saldo_awal", "jumlah_stok", "stok_tersedia", "jumlah_barang", "stock", "stok");
                if (saldoAwal != 0) stok.put(id, saldoAwal);
            }
        }

        // Tambahkan transaksi masuk dan kurangi transaksi keluar agar dashboard selalu dihitung dari data terbaru.
        if (transaksi != null && transaksi.isArray()) {
            for (JsonNode row : transaksi) {
                int id = (int) angkaPertama(row, "barang_id", "id_barang", "barangId", "idbarang");
                if (id <= 0) continue;
                long qty = angkaPertama(row, "jumlah", "kuantitas", "qty");
                String jenis = jenisTransaksi(row);
                if ("Masuk".equals(jenis)) stok.merge(id, qty, Long::sum);
                else if ("Keluar".equals(jenis)) stok.merge(id, -qty, Long::sum);
            }
        }
        return stok;
    }

    private long angkaPertama(JsonNode row, String... kolom) {
        if (row == null) return 0;
        for (String kolomNama : kolom) {
            JsonNode nilai = row.get(kolomNama);
            if (nilai != null && !nilai.isNull() && !nilai.asText().isBlank()) {
                try { return Long.parseLong(nilai.asText().trim()); }
                catch (NumberFormatException ignored) {
                    try { return (long) Double.parseDouble(nilai.asText().trim()); }
                    catch (NumberFormatException ignoredAgain) { }
                }
            }
        }
        return 0;
    }

    private String jenisTransaksi(JsonNode row) {
        String jenis = row.path("jenis").asText("").trim().toLowerCase(Locale.ROOT);
        if (jenis.isBlank()) jenis = row.path("jenis_transaksi").asText("").trim().toLowerCase(Locale.ROOT);
        if (jenis.isBlank()) jenis = row.path("tipe_transaksi").asText("").trim().toLowerCase(Locale.ROOT);
        if (jenis.contains("masuk") || jenis.equals("in") || jenis.equals("incoming")) return "Masuk";
        if (jenis.contains("keluar") || jenis.equals("out") || jenis.equals("outgoing")) return "Keluar";
        return "";
    }

    private String tabelPersediaan(JsonNode barang, Map<Integer, Long> stok) {
        if (barang == null || !barang.isArray() || barang.isEmpty()) {
            return "<div class='empty-state'>Belum ada barang di master persediaan.</div>";
        }
        StringBuilder html = new StringBuilder("<div class='table-wrap'><table><thead><tr>")
            .append("<th>Nama Barang</th><th>Kategori</th><th>Satuan</th>")
            .append("<th>Estimasi Stok</th><th>Pantau Kedaluwarsa</th></tr></thead><tbody>");
        for (JsonNode row : barang) {
            int id = (int) angkaPertama(row, "id", "barang_id", "id_barang");
            long jumlah = stok.getOrDefault(id, angkaPertama(row, "stok_awal", "saldo_awal", "jumlah_stok", "stok_tersedia", "jumlah_barang", "stock", "stok"));
            html.append("<tr><td><strong>").append(escape(row.path("nama_barang").asText("-"))).append("</strong></td>")
                .append("<td>").append(escape(row.path("kategori").asText("-"))).append("</td>")
                .append("<td>").append(escape(row.path("satuan").asText("-"))).append("</td>")
                .append("<td><strong>").append(jumlah).append("</strong></td><td>")
                .append(row.path("tracking_expired").asBoolean(false)
                    ? "<span class='status-out'>Dipantau</span>" : "<span class='soft-pill'>Tidak dipantau</span>")
                .append("</td></tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    private String chartTren(JsonNode transaksi) {
        Map<String, long[]> perBulan = new TreeMap<>();
        if (transaksi != null && transaksi.isArray()) {
            for (JsonNode row : transaksi) {
                String tanggal = row.path("tanggal").asText("");
                if (tanggal.length() < 7) continue;
                String bulan = tanggal.substring(0, 7);
                long[] vals = perBulan.computeIfAbsent(bulan, k -> new long[2]);
                String jenis = jenisTransaksi(row);
                if (jenis.isBlank()) continue;
                int idx = "Masuk".equals(jenis) ? 0 : 1;
                vals[idx] += angkaPertama(row, "jumlah", "kuantitas", "qty");
            }
        }
        List<Map.Entry<String, long[]>> entries = new ArrayList<>(perBulan.entrySet());
        if (entries.size() > 6) entries = entries.subList(entries.size() - 6, entries.size());
        if (entries.isEmpty()) {
            return "<div class='chart-panel'><h3>Tren Transaksi Bulanan</h3><div class='chart-desc'>Jumlah barang masuk dan keluar.</div><div class='empty-state'>Grafik akan muncul setelah transaksi tercatat.</div></div>";
        }
        int w = 500, h = 210, left = 42, top = 15, plotW = 430, plotH = 145;
        long max = 1;
        for (var e : entries) max = Math.max(max, Math.max(e.getValue()[0], e.getValue()[1]));
        StringBuilder svg = new StringBuilder("<svg viewBox='0 0 500 220' role='img' aria-label='Grafik garis barang masuk dan keluar'>");
        for (int i = 0; i <= 3; i++) {
            int y = top + i * plotH / 3;
            svg.append("<line x1='").append(left).append("' y1='").append(y).append("' x2='480' y2='").append(y).append("' stroke='#e9eef3'/>");
        }
        StringBuilder masukPts = new StringBuilder(), keluarPts = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            int x = entries.size() == 1 ? left + plotW / 2 : left + i * plotW / (entries.size() - 1);
            int yM = top + plotH - (int) Math.round(entries.get(i).getValue()[0] * 1.0 / max * plotH);
            int yK = top + plotH - (int) Math.round(entries.get(i).getValue()[1] * 1.0 / max * plotH);
            masukPts.append(x).append(",").append(yM).append(" ");
            keluarPts.append(x).append(",").append(yK).append(" ");
            String label = entries.get(i).getKey().substring(5);
            svg.append("<text x='").append(x).append("' y='184' text-anchor='middle' font-size='11' fill='#728196'>").append(label).append("</text>");
            svg.append("<circle cx='").append(x).append("' cy='").append(yM).append("' r='4' fill='#16845b'/>")
               .append("<circle cx='").append(x).append("' cy='").append(yK).append("' r='4' fill='#f15a24'/>");
        }
        svg.append("<polyline points='").append(masukPts).append("' fill='none' stroke='#16845b' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'/>")
           .append("<polyline points='").append(keluarPts).append("' fill='none' stroke='#f15a24' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'/>")
           .append("</svg>");
        return "<div class='chart-panel'><h3>Tren Transaksi Bulanan</h3><div class='chart-desc'>Jumlah unit masuk dibandingkan unit keluar.</div><div class='chart-canvas'>" + svg + "</div><div class='chart-legend'><span><i class='legend-dot' style='background:#16845b'></i>Barang masuk</span><span><i class='legend-dot' style='background:#f15a24'></i>Barang keluar</span></div></div>";
    }

    private String chartBatang(JsonNode barang, Map<Integer, Long> stok) {
        List<JsonNode> data = new ArrayList<>();
        if (barang != null && barang.isArray()) barang.forEach(data::add);
        data.sort((a, b) -> Long.compare(
            stok.getOrDefault((int) angkaPertama(b, "id", "barang_id", "id_barang"), 0L),
            stok.getOrDefault((int) angkaPertama(a, "id", "barang_id", "id_barang"), 0L)));
        if (data.size() > 6) data = data.subList(0, 6);
        long max = 1;
        for (JsonNode row : data) max = Math.max(max, Math.max(0,
            stok.getOrDefault((int) angkaPertama(row, "barang_id", "id", "id_barang"), 0L)));
        StringBuilder svg = new StringBuilder("<svg viewBox='0 0 420 220' role='img' aria-label='Grafik batang stok barang'>");
        int y = 15;
        for (JsonNode row : data) {
            String nama = row.path("nama_barang").asText("-");
            if (nama.length() > 17) nama = nama.substring(0, 16) + "…";
            long nilai = Math.max(0, stok.getOrDefault((int) angkaPertama(row, "id", "barang_id", "id_barang"), 0L));
            int width = (int) Math.round(nilai * 1.0 / max * 230);
            svg.append("<text x='0' y='").append(y + 13).append("' font-size='11' fill='#53667a'>").append(escape(nama)).append("</text>")
               .append("<rect x='142' y='").append(y).append("' width='230' height='16' rx='6' fill='#edf1f5'/>")
               .append("<rect x='142' y='").append(y).append("' width='").append(width).append("' height='16' rx='6' fill='#f15a24'/>")
               .append("<text x='382' y='").append(y + 13).append("' font-size='11' fill='#142b45' font-weight='700'>").append(nilai).append("</text>");
            y += 31;
        }
        svg.append("</svg>");
        if (data.isEmpty()) return "<div class='chart-panel'><h3>Jumlah Stok per Barang</h3><div class='chart-desc'>Enam barang dengan estimasi stok tertinggi.</div><div class='empty-state'>Belum ada data barang.</div></div>";
        return "<div class='chart-panel'><h3>Jumlah Stok per Barang</h3><div class='chart-desc'>Maksimal enam barang berdasarkan estimasi stok.</div><div class='chart-canvas'>" + svg + "</div></div>";
    }

    private String chartPie(JsonNode barang) {
        Map<String, Integer> kategori = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (barang != null && barang.isArray()) {
            for (JsonNode row : barang) {
                String k = row.path("kategori").asText("Belum dikategorikan");
                if (k == null || k.isBlank()) k = "Belum dikategorikan";
                kategori.merge(k, 1, Integer::sum);
            }
        }
        if (kategori.isEmpty()) return "<div class='chart-panel'><h3>Komposisi Kategori</h3><div class='chart-desc'>Proporsi jenis barang per kategori.</div><div class='empty-state'>Belum ada data kategori.</div></div>";
        String[] colors = {"#f15a24", "#203e5d", "#16845b", "#e5a23d", "#7188a3", "#b75d9b", "#5d9ca8"};
        double total = kategori.values().stream().mapToInt(Integer::intValue).sum();
        double angle = 0;
        StringBuilder slices = new StringBuilder();
        int ci = 0;
        for (var entry : kategori.entrySet()) {
            double sweep = entry.getValue() / total * 360.0;
            double end = angle + sweep;
            double x1 = 90 + 65 * Math.cos(Math.toRadians(angle));
            double y1 = 90 + 65 * Math.sin(Math.toRadians(angle));
            double x2 = 90 + 65 * Math.cos(Math.toRadians(end));
            double y2 = 90 + 65 * Math.sin(Math.toRadians(end));
            int large = sweep > 180 ? 1 : 0;
            if (kategori.size() == 1) {
                slices.append("<circle cx='90' cy='90' r='65' fill='").append(colors[ci % colors.length]).append("'/>");
            } else {
                slices.append("<path d='M 90 90 L ").append(String.format(Locale.US, "%.2f %.2f", x1, y1))
                    .append(" A 65 65 0 ").append(large).append(" 1 ")
                    .append(String.format(Locale.US, "%.2f %.2f", x2, y2)).append(" Z' fill='")
                    .append(colors[ci % colors.length]).append("'/>");
            }
            angle = end; ci++;
        }
        StringBuilder legend = new StringBuilder("<div class='chart-legend' style='display:grid;grid-template-columns:1fr;gap:7px'>");
        ci = 0;
        for (var entry : kategori.entrySet()) {
            int persen = (int) Math.round(entry.getValue() / total * 100);
            legend.append("<span><i class='legend-dot' style='background:").append(colors[ci++ % colors.length]).append("'></i>")
                .append(escape(entry.getKey())).append(" <b>").append(persen).append("%</b></span>");
        }
        legend.append("</div>");
        return "<div class='chart-panel'><h3>Komposisi Kategori</h3><div class='chart-desc'>Proporsi jumlah jenis barang.</div><div style='display:flex;justify-content:center'><svg viewBox='0 0 180 180' style='width:170px;max-width:100%' role='img' aria-label='Diagram pie kategori'>" + slices + "</svg></div>" + legend + "</div>";
    }

    // ---------- DASHBOARD KEBENCANAAN ----------

    @GetMapping("/kejadian")
    @ResponseBody
    public String kejadian() {
        StringBuilder isi = new StringBuilder();

        try {
            JsonNode data = ambilData("kejadian_bencana");
            JsonNode mitigasi = ambilData("mitigasi_bencana");
            JsonNode pasca = ambilData("pascabencana");

            int aktif = 0;
            Map<String, Integer> jenis = new TreeMap<>();

            for (JsonNode row : data) {
                String status = row.path("status").asText("");
                if (!"selesai".equalsIgnoreCase(status)) aktif++;

                String nama = row.path("jenis_bencana")
                        .asText("Tidak diisi");
                jenis.merge(nama, 1, Integer::sum);
            }

            isi.append("<div class='grid'>")
                    .append(kartu("⚠", "Total Kejadian", data.size()))
                    .append(kartu("◷", "Masih Ditangani", aktif))
                    .append(kartu("✓", "Kegiatan Mitigasi", mitigasi.size()))
                    .append(kartu("▤", "Data Pascabencana", pasca.size()))
                    .append("</div>");

            isi.append("<div class='panel'><h2>Kejadian Menurut Jenis</h2>")
                    .append("<table><tr><th>Jenis Bencana</th>")
                    .append("<th>Jumlah</th></tr>");

            for (var entry : jenis.entrySet()) {
                isi.append("<tr><td>").append(escape(entry.getKey()))
                        .append("</td><td>").append(entry.getValue())
                        .append("</td></tr>");
            }

            isi.append("</table></div>")
                    .append("<div class='panel'><h2>Daftar Kejadian</h2>")
                    .append("<a class='button' href='/input-kejadian'>")
                    .append("+ Input Kejadian</a>")
                    .append(tabelJson(data))
                    .append("</div>")
                    .append("<div class='panel'><h2>Kegiatan Mitigasi</h2>")
                    .append(tabelJson(mitigasi))
                    .append("</div>")
                    .append("<div class='panel'><h2>Data Pascabencana</h2>")
                    .append(tabelJson(pasca))
                    .append("</div>");

        } catch (Exception e) {
            isi.append(pesan(e.getMessage()));
        }

        return halaman("Dashboard Kebencanaan", isi.toString());
    }

    // ---------- FORM INPUT KEJADIAN ----------

    @GetMapping("/input-kejadian")
    @ResponseBody
    public String formKejadian() {
        String isi = """
        <div class="panel">
          <h2>Formulir Kejadian Bencana</h2>
          <form method="post" action="/input-kejadian">
            <label>Tanggal Kejadian</label>
            <input type="date" name="tanggal" value="%s" required>

            <label>ID Wilayah</label>
            <input type="number" name="wilayah_id" min="1" required>

            <label>Jenis Bencana</label>
            <select name="jenis_bencana" required>
              <option>Banjir</option>
              <option>Tanah longsor</option>
              <option>Angin kencang</option>
              <option>Kekeringan</option>
              <option>Lainnya</option>
            </select>

            <label>Detail Lokasi</label>
            <input type="text" name="lokasi" required>

            <label>Deskripsi Kejadian</label>
            <textarea name="deskripsi"></textarea>

            <label>Status Penanganan</label>
            <select name="status">
              <option>Dilaporkan</option>
              <option>Dalam Penanganan</option>
              <option>Selesai</option>
            </select>

            <button type="submit">Simpan Kejadian</button>
          </form>
        </div>
        """.formatted(LocalDate.now());

        return halaman("Input Kejadian", isi);
    }

    @PostMapping("/input-kejadian")
    @ResponseBody
    public String simpanKejadian(
            @RequestParam String tanggal,
            @RequestParam String wilayah_id,
            @RequestParam String jenis_bencana,
            @RequestParam String lokasi,
            @RequestParam(defaultValue = "") String deskripsi,
            @RequestParam(defaultValue = "Dilaporkan") String status) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tanggal", tanggal);
            data.put("wilayah_id", Integer.parseInt(wilayah_id));
            data.put("jenis_bencana", jenis_bencana);
            data.put("lokasi", lokasi);
            data.put("deskripsi", deskripsi);
            data.put("status", status);

            simpanData("kejadian_bencana", data);
            return halaman("Berhasil", pesan("Kejadian berhasil disimpan."));
        } catch (Exception e) {
            return halaman("Gagal", pesan("Gagal menyimpan: " + e.getMessage()));
        }
    }

    // ---------- FORM TRANSAKSI LOGISTIK ----------

    @GetMapping("/input-transaksi")
    @ResponseBody
    public String formTransaksi(@RequestParam(required = false) String jenis) {
        if (jenis == null || jenis.isBlank()) {
            String isi = """
            <div class="form-shell">
              <div class="section-heading" style="margin-top:0"><div>
                <div class="section-kicker">Pencatatan pergerakan barang</div>
                <h2>Transaksi Logistik</h2>
                <p>Pilih jenis transaksi untuk membuka formulir yang sesuai.</p>
              </div></div>
              <div class="choice-grid">
                <a class="choice-card choice-in" href="/input-transaksi?jenis=Masuk">
                  <div class="choice-icon">↓</div><h2>Barang Masuk</h2>
                  <p>Pencatatan penerimaan bantuan dari donatur, instansi, organisasi, atau sumber lainnya.</p>
                </a>
                <a class="choice-card choice-out" href="/input-transaksi?jenis=Keluar">
                  <div class="choice-icon">↑</div><h2>Barang Keluar</h2>
                  <p>Pencatatan penyaluran logistik ke kecamatan atau lokasi penanganan bencana.</p>
                </a>
              </div>
              <div class="form-actions"><a class="button" href="/" style="background:#203e5d">Kembali ke Dashboard</a></div>
            </div>
            """;
            return halaman("Transaksi Logistik", isi);
        }

        boolean masuk = "Masuk".equalsIgnoreCase(jenis);
        if (!masuk && !"Keluar".equalsIgnoreCase(jenis)) {
            return halaman("Transaksi Logistik", pesan("Jenis transaksi tidak valid."));
        }

        StringBuilder isi = new StringBuilder();
        try {
            JsonNode barang = masterBarangUnik(ambilData("master_barang"));
            StringBuilder options = new StringBuilder("<option value=''>Pilih barang...</option>");
            for (JsonNode row : barang) {
                // Skema master_barang bisa memakai salah satu nama kolom ID berikut.
                // Fallback ini penting: jika hanya membaca kolom yang tidak ada,
                // semua opsi bernilai 0 sehingga barang berbeda dianggap duplikat.
                int id = row.path("id").asInt(
                        row.path("barang_id").asInt(
                        row.path("id_barang").asInt(0)));
                String nama = row.path("nama_barang").asText(
                        row.path("nama").asText("Barang tanpa nama"));
                String satuan = row.path("satuan").asText("");

                // Jangan tampilkan opsi tanpa ID numerik yang valid.
                // Endpoint penyimpanan memang menerima barang_id bertipe integer.
                if (id <= 0) {
                    continue;
                }

                options.append("<option value='").append(id).append("'>")
                    .append(escape(nama)).append(satuan.isBlank() ? "" : " (" + escape(satuan) + ")")
                    .append("</option>");
            }

            if (options.toString().equals("<option value=''>Pilih barang...</option>")) {
                options.append("<option value='' disabled>Data barang belum memiliki ID numerik yang dikenali</option>");
            }

            isi.append("<div class='form-shell'><div class='panel'>")
                .append("<div class='form-title-row'><div class='form-title-icon ")
                .append(masuk ? "in" : "out").append("'>").append(masuk ? "↓" : "↑")
                .append("</div><div><div class='section-kicker'>Form transaksi</div><h2>")
                .append(masuk ? "Penerimaan Barang" : "Penyaluran Barang")
                .append("</h2><div class='panel-subtitle'>")
                .append(masuk ? "Catat bantuan yang diterima dan asal sumbernya." : "Catat distribusi bantuan dan tujuan penyalurannya.")
                .append("</div></div></div>")
                .append("<form method='post' action='/input-transaksi' id='transactionForm'>")
                .append("<input type='hidden' name='jenis' value='").append(masuk ? "Masuk" : "Keluar").append("'>")
                .append("<div class='form-section'><h3>Informasi transaksi</h3><p>")
                .append(masuk ? "Lengkapi tanggal penerimaan barang." : "Lengkapi tanggal dan kecamatan tujuan penyaluran.")
                .append("</p><div class='form-grid'>")
                .append("<div class='form-field'><label for='tanggal'>Tanggal transaksi</label><input id='tanggal' type='date' name='tanggal' value='")
                .append(LocalDate.now()).append("' required></div>");
            if (!masuk) {
                isi.append("<div class='form-field'><label for='kecamatan'>Kecamatan tujuan di Kota Semarang</label><select id='kecamatan' name='kecamatan' required>")
                    .append("<option value=''>Pilih kecamatan...</option>");
                String[] kecamatan = {"Banyumanik","Candisari","Gajahmungkur","Gayamsari","Genuk","Gunungpati","Mijen","Ngaliyan","Pedurungan","Semarang Barat","Semarang Selatan","Semarang Tengah","Semarang Timur","Semarang Utara","Tembalang","Tugu"};
                for (String k : kecamatan) isi.append("<option>").append(escape(k)).append("</option>");
                isi.append("</select></div>");
            }
            isi.append("</div></div>");

            isi.append("<div class='form-section'><h3>Daftar barang</h3><p>Pilih satu atau beberapa barang. Tambahkan baris untuk setiap jenis barang dan atur kuantitasnya.</p>")
                .append("<div id='itemList'><div class='item-row'><div><label>Nama barang</label><select name='barang_id' required>")
                .append(options).append("</select></div><div><label>Kuantitas</label><input type='number' name='jumlah' min='1' step='1' value='1' required></div>")
                .append("<button class='remove-item' type='button' aria-label='Hapus barang' onclick='removeItem(this)' title='Hapus baris'>×</button></div></div>")
                .append("<button class='button' type='button' id='addItem' style='background:#203e5d;box-shadow:none'>+ Tambah barang</button></div>");

            if (masuk) {
                isi.append("<div class='form-section'><h3>Asal bantuan</h3><p>Catat pihak atau lokasi asal barang yang diterima.</p><div class='form-grid'>")
                    .append("<div class='form-field full'><label for='sumber_barang'>Sumber barang</label><input id='sumber_barang' name='sumber_barang' placeholder='Contoh: BPBD Provinsi, PMI, donatur, perusahaan...' required></div>")
                    .append("<div class='form-field full'><label for='keterangan'>Keterangan tambahan</label><textarea id='keterangan' name='keterangan' placeholder='Nomor surat jalan, kondisi penerimaan, atau catatan lain'></textarea></div>")
                    .append("</div></div>");
            } else {
                isi.append("<div class='form-section'><h3>Tujuan penyaluran</h3><p>Catat kebutuhan atau kejadian yang menjadi alasan penyaluran logistik.</p><div class='form-grid'>")
                    .append("<div class='form-field full'><label for='tujuan_penggunaan'>Untuk keperluan / bencana</label><input id='tujuan_penggunaan' name='tujuan_penggunaan' placeholder='Contoh: bantuan banjir, tanah longsor, pengungsian...' required></div>")
                    .append("<div class='form-field'><label for='penerima'>Penerima / lokasi tujuan</label><input id='penerima' name='penerima' placeholder='Nama posko, kelurahan, atau instansi'></div>")
                    .append("<div class='form-field full'><label for='keterangan'>Keterangan tambahan</label><textarea id='keterangan' name='keterangan' placeholder='Catatan distribusi atau nomor dokumen'></textarea></div>")
                    .append("</div></div>");
            }

            if (masuk) {
                isi.append("<input type='hidden' name='kondisi' value='Baik'>");
            }
            isi.append("<div class='form-actions'><button type='submit'>Simpan Transaksi</button>")
                .append("<a class='button' href='/input-transaksi' style='background:#203e5d;box-shadow:none'>Batal</a></div></form></div></div>")
                .append("<script>")
                .append("const itemList=document.getElementById('itemList');")
                .append("const addItem=document.getElementById('addItem');")
                .append("const itemOptions=").append(jsString(options.toString())).append(";")
                .append("addItem.addEventListener('click',()=>{const row=document.createElement('div');row.className='item-row';row.innerHTML='<div><label>Nama barang</label><select name=\"barang_id\" required>'+itemOptions+'</select></div><div><label>Kuantitas</label><input type=\"number\" name=\"jumlah\" min=\"1\" step=\"1\" value=\"1\" required></div><button class=\"remove-item\" type=\"button\" aria-label=\"Hapus barang\" onclick=\"removeItem(this)\">×</button>';itemList.appendChild(row);});")
                .append("function removeItem(button){const rows=itemList.querySelectorAll('.item-row');if(rows.length>1){button.closest('.item-row').remove();}else{const select=button.closest('.item-row').querySelector('select');select.value='';button.closest('.item-row').querySelector('input').value='1';}}")
                .append("document.getElementById('transactionForm').addEventListener('submit',function(e){const selects=[...this.querySelectorAll('select[name=\"barang_id\"]')];const ids=selects.map(x=>x.value);if(ids.some(x=>!x)){e.preventDefault();alert('Pilih barang pada semua baris.');return;}const seen=new Set();for(let i=0;i<ids.length;i++){if(seen.has(ids[i])){e.preventDefault();alert('Barang yang sama dipilih dua kali: '+selects[i].selectedOptions[0].text+'. Pilih setiap barang satu kali saja dan gabungkan kuantitasnya pada baris tersebut.');return;}seen.add(ids[i]);}});")
                .append("</script>");

        } catch (Exception e) {
            isi.append(pesan("Gagal memuat formulir transaksi: " + e.getMessage()));
        }
        return halaman(masuk ? "Barang Masuk" : "Barang Keluar", isi.toString());
    }

    private String jsString(String value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    @PostMapping("/input-transaksi")
    @ResponseBody
    public String simpanTransaksi(
            @RequestParam String tanggal,
            @RequestParam String jenis,
            @RequestParam(name = "barang_id") List<Integer> barangIds,
            @RequestParam(name = "jumlah") List<Integer> jumlahList,
            @RequestParam(defaultValue = "") String kecamatan,
            @RequestParam(defaultValue = "") String sumber_barang,
            @RequestParam(defaultValue = "") String tujuan_penggunaan,
            @RequestParam(defaultValue = "") String penerima,
            @RequestParam(defaultValue = "Baik") String kondisi,
            @RequestParam(defaultValue = "") String keterangan) {
        try {
            if (barangIds == null || jumlahList == null || barangIds.isEmpty() || barangIds.size() != jumlahList.size()) {
                throw new IllegalArgumentException("Daftar barang dan kuantitas tidak valid.");
            }
            if (!"Masuk".equalsIgnoreCase(jenis) && !"Keluar".equalsIgnoreCase(jenis)) {
                throw new IllegalArgumentException("Jenis transaksi tidak valid.");
            }
            if ("Masuk".equalsIgnoreCase(jenis) && sumber_barang.isBlank()) {
                throw new IllegalArgumentException("Sumber barang wajib diisi.");
            }
            if ("Keluar".equalsIgnoreCase(jenis) && tujuan_penggunaan.isBlank()) {
                throw new IllegalArgumentException("Tujuan penggunaan wajib diisi.");
            }
            if ("Keluar".equalsIgnoreCase(jenis) && kecamatan.isBlank()) {
                throw new IllegalArgumentException("Kecamatan tujuan wajib dipilih untuk barang keluar.");
            }

            for (int i = 0; i < barangIds.size(); i++) {
                if (barangIds.get(i) == null || barangIds.get(i) < 1 || jumlahList.get(i) == null || jumlahList.get(i) < 1) {
                    throw new IllegalArgumentException("Pilih barang dan masukkan kuantitas minimal 1.");
                }
            }

            for (int i = 0; i < barangIds.size(); i++) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("tanggal", tanggal);
                data.put("barang_id", barangIds.get(i));
                data.put("jenis", jenis);
                data.put("jumlah", jumlahList.get(i));
                data.put("kondisi", kondisi);
                data.put("keterangan", keterangan);
                if ("Keluar".equalsIgnoreCase(jenis)) data.put("kecamatan", kecamatan);
                data.put("sumber_barang", "Masuk".equalsIgnoreCase(jenis) ? sumber_barang : null);
                data.put("tujuan_penggunaan", "Keluar".equalsIgnoreCase(jenis) ? tujuan_penggunaan : null);
                data.put("penerima", penerima);
                simpanData("logistik_transaksi", data);
            }
            return "<!doctype html><html lang='id'><head><meta charset='UTF-8'><meta http-equiv='refresh' content='0;url=/logistik'><title>Transaksi tersimpan</title></head><body><p>Transaksi berhasil disimpan. Dashboard sedang diperbarui...</p><script>window.location.replace('/logistik');</script><a href='/logistik'>Buka dashboard</a></body></html>";
        } catch (Exception e) {
            return halaman("Transaksi Gagal", pesan("Gagal menyimpan transaksi: " + e.getMessage()));
        }
    }

    // ---------- PERMINTAAN DATA ----------

    @GetMapping("/permintaan")
    @ResponseBody
    public String formPermintaan() {
        String isi = """
        <div class="panel">
          <h2>Pencatatan Permintaan Data</h2>
          <form method="post" action="/permintaan">
            <label>Nama Pemohon</label>
            <input name="pemohon" required>

            <label>Instansi</label>
            <input name="instansi">

            <label>Data yang Diminta</label>
            <textarea name="kebutuhan_data" required></textarea>

            <label>Tujuan Penggunaan Data</label>
            <textarea name="tujuan"></textarea>

            <label>Status</label>
            <select name="status">
              <option>Baru</option>
              <option>Diproses</option>
              <option>Menunggu</option>
              <option>Selesai</option>
              <option>Ditolak</option>
            </select>

            <label>Tenggat Penyelesaian</label>
            <input type="date" name="deadline" value="%s" required>

            <button type="submit">Simpan Permintaan</button>
          </form>
        </div>
        <div class="panel">
          <h2>Daftar Permintaan</h2>
          <a class="button" href="/data?table=permintaan_data">
            Lihat Data Tersimpan
          </a>
        </div>
        """.formatted(LocalDate.now());

        return halaman("Permintaan Data", isi);
    }

    @PostMapping("/permintaan")
    @ResponseBody
    public String simpanPermintaan(
            @RequestParam String pemohon,
            @RequestParam(defaultValue = "") String instansi,
            @RequestParam String kebutuhan_data,
            @RequestParam(defaultValue = "") String tujuan,
            @RequestParam String status,
            @RequestParam String deadline) {
        try {
            if (pemohon.isBlank() || kebutuhan_data.isBlank()) {
                return halaman("Validasi", pesan(
                        "Nama pemohon dan kebutuhan data wajib diisi."));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pemohon", pemohon);
            data.put("instansi", instansi);
            data.put("kebutuhan_data", kebutuhan_data);
            data.put("tujuan", tujuan);
            data.put("status", status);
            data.put("deadline", deadline);

            simpanData("permintaan_data", data);
            return halaman("Berhasil", pesan(
                    "Permintaan data berhasil disimpan."));
        } catch (Exception e) {
            return halaman("Gagal", pesan("Gagal menyimpan: " + e.getMessage()));
        }
    }

    // ---------- DATA TERSIMPAN ----------

    @GetMapping("/data")
    @ResponseBody
    public String dataTersimpan(
            @RequestParam(defaultValue = "kejadian_bencana") String table) {
        Set<String> tabelDiizinkan = Set.of(
                "master_barang",
                "logistik_transaksi",
                "permintaan_data",
                "kejadian_bencana",
                "mitigasi_bencana",
                "pascabencana",
                "master_wilayah");

        if (!tabelDiizinkan.contains(table)) {
            return halaman("Data Tersimpan",
                    pesan("Nama tabel tidak diizinkan."));
        }

        try {
            JsonNode data = ambilData(table);

            String isi = """
            <div class="panel">
              <h2>Data Tersimpan</h2>
              <form method="get" action="/data">
                <label>Pilih Tabel</label>
                <select name="table">
                  <option value="master_barang">Master Barang</option>
                  <option value="logistik_transaksi">Transaksi Logistik</option>
                  <option value="permintaan_data">Permintaan Data</option>
                  <option value="kejadian_bencana">Kejadian Bencana</option>
                  <option value="mitigasi_bencana">Mitigasi Bencana</option>
                  <option value="pascabencana">Pascabencana</option>
                  <option value="master_wilayah">Master Wilayah</option>
                </select>
                <button type="submit">Tampilkan Data</button>
              </form>
              <p class="muted">Tabel aktif: %s · Jumlah baris: %d</p>
              %s
            </div>
            """.formatted(escape(table), data.size(), tabelJson(data));

            return halaman("Data Tersimpan", isi);
        } catch (Exception e) {
            return halaman("Data Tersimpan", pesan(
                    "Gagal membaca data: " + e.getMessage()));
        }
    }

    private String tabelJson(JsonNode rows) {
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return "<p class='muted'>Belum ada data untuk ditampilkan.</p>";
        }

        LinkedHashSet<String> kolom = new LinkedHashSet<>();
        for (JsonNode row : rows) {
            row.fieldNames().forEachRemaining(kolom::add);
        }

        StringBuilder html = new StringBuilder("<div class='table-wrap'><table><thead><tr>");
        for (String nama : kolom) {
            html.append("<th>").append(escape(nama)).append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (JsonNode row : rows) {
            html.append("<tr>");
            for (String nama : kolom) {
                JsonNode value = row.get(nama);
                String text = value == null || value.isNull()
                        ? "-" : value.isContainerNode()
                        ? value.toString() : value.asText();
                html.append("<td>").append(escape(text)).append("</td>");
            }
            html.append("</tr>");
        }

        return html.append("</tbody></table></div>").toString();
    }
}
