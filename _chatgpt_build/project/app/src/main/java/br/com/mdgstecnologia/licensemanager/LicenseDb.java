package br.com.mdgstecnologia.licensemanager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LicenseDb {
    int version = 2;
    String generatedUtc = Instant.now().toString();
    final List<Product> products = new ArrayList<>();
    final List<Company> companies = new ArrayList<>();
    final List<Device> devices = new ArrayList<>();

    static final class Product {
        String productId = "";
        String name = "";

        Product() {}
        Product(String productId, String name) {
            this.productId = productId == null ? "" : productId.trim().toLowerCase(Locale.ROOT);
            this.name = upper(name);
        }
    }

    static final class Company {
        String id = "";
        String name = "";
        String cnpj = "";
        String productId = "";
        String softwareName = "";
        String login = "";
        String salt = "";
        String passwordHash = "";
        String profile = "EMPRESA";
        String validUntil = null;
        int deviceLimit = 0;
        boolean accessAllProducts = false;
        boolean deviceRequired = true;
        String status = "ACTIVE";
    }

    static final class Device {
        String id = "";
        String companyId = "";
        String platform = "PC";
        String deviceId = "";
        String friendlyName = "";
        String status = "ACTIVE";
        String linkedUtc = Instant.now().toString();
        String updatedUtc = Instant.now().toString();
    }

    static LicenseDb newEmpty() {
        LicenseDb db = new LicenseDb();
        db.ensureDefaultProducts();
        return db;
    }

    static LicenseDb fromJson(JSONObject root) throws JSONException {
        LicenseDb db = new LicenseDb();
        db.version = root.optInt("Version", 2);
        db.generatedUtc = root.optString("GeneratedUtc", Instant.now().toString());

        boolean hasProductCatalog = root.has("Products");
        JSONArray ps = root.optJSONArray("Products");
        if (ps != null) {
            for (int i = 0; i < ps.length(); i++) {
                JSONObject o = ps.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("ProductId", "").trim().toLowerCase(Locale.ROOT);
                String name = upper(o.optString("Name", ""));
                if (!id.isEmpty() && !name.isEmpty() && !"*".equals(id) && !"ACESSO TOTAL".equals(name)) {
                    db.products.add(new Product(id, name));
                }
            }
        }
        if (!hasProductCatalog) db.ensureDefaultProducts();

        JSONArray cs = root.optJSONArray("Companies");
        if (cs != null) {
            for (int i = 0; i < cs.length(); i++) {
                JSONObject o = cs.optJSONObject(i);
                if (o == null) continue;
                Company c = new Company();
                c.id = o.optString("Id", "").trim();
                c.name = o.optString("Name", "");
                c.cnpj = o.optString("Cnpj", "");
                c.productId = o.optString("ProductId", "").trim().toLowerCase(Locale.ROOT);
                c.softwareName = o.optString("SoftwareName", "");
                c.login = o.optString("Login", "");
                c.salt = o.optString("Salt", "");
                c.passwordHash = o.optString("PasswordHash", "");
                c.profile = o.optString("Profile", "EMPRESA");
                c.validUntil = o.isNull("ValidUntil") ? null : o.optString("ValidUntil", null);
                c.accessAllProducts = o.optBoolean("AccessAllProducts", false);
                c.deviceRequired = o.has("DeviceRequired") ? o.optBoolean("DeviceRequired", true) : true;
                if (o.has("DeviceLimit")) c.deviceLimit = Math.max(0, o.optInt("DeviceLimit", 0));
                else c.deviceLimit = Math.max(0, o.optInt("PcLimit", 0) + o.optInt("AndroidLimit", 0));
                c.status = o.optString("Status", "ACTIVE");
                db.companies.add(c);
            }
        }

        JSONArray ds = root.optJSONArray("Devices");
        if (ds != null) {
            for (int i = 0; i < ds.length(); i++) {
                JSONObject o = ds.optJSONObject(i);
                if (o == null) continue;
                Device d = new Device();
                d.id = o.optString("Id", "");
                d.companyId = o.optString("CompanyId", "").trim();
                d.platform = o.optString("Platform", "");
                d.deviceId = o.optString("DeviceId", "");
                d.friendlyName = o.optString("FriendlyName", "");
                d.status = o.optString("Status", "ACTIVE");
                d.linkedUtc = o.optString("LinkedUtc", Instant.now().toString());
                d.updatedUtc = o.optString("UpdatedUtc", d.linkedUtc);
                db.devices.add(d);
            }
        }
        db.normalize(!hasProductCatalog);
        return db;
    }

    JSONObject toJsonForEncryption() throws JSONException {
        normalize(false);
        JSONObject root = new JSONObject();
        root.put("Version", 2);
        root.put("GeneratedUtc", Instant.now().toString());

        JSONArray ps = new JSONArray();
        for (Product p : products) {
            JSONObject o = new JSONObject();
            o.put("ProductId", p.productId);
            o.put("Name", p.name);
            ps.put(o);
        }
        root.put("Products", ps);

        JSONArray cs = new JSONArray();
        for (Company c : companies) {
            JSONObject o = new JSONObject();
            o.put("Id", c.id);
            o.put("Name", c.name);
            o.put("Cnpj", c.cnpj == null ? "" : c.cnpj);
            o.put("ProductId", c.productId == null ? "" : c.productId);
            o.put("SoftwareName", c.softwareName == null ? "" : c.softwareName);
            o.put("AccessAllProducts", isAdmin(c));
            o.put("DeviceRequired", !isAdmin(c));
            o.put("Login", c.login);
            o.put("Salt", c.salt);
            o.put("PasswordHash", c.passwordHash);
            o.put("Profile", c.profile);
            o.put("ValidUntil", c.validUntil == null ? JSONObject.NULL : c.validUntil);
            o.put("DeviceLimit", Math.max(0, c.deviceLimit));
            o.put("PcLimit", Math.max(0, c.deviceLimit));
            o.put("AndroidLimit", Math.max(0, c.deviceLimit));
            o.put("Status", c.status);
            cs.put(o);
        }

        JSONArray ds = new JSONArray();
        for (Device d : devices) {
            JSONObject o = new JSONObject();
            o.put("Id", d.id);
            o.put("CompanyId", d.companyId);
            String p = upper(d.platform);
            if (p.isEmpty()) p = platformFromDeviceId(d.deviceId);
            if (p.isEmpty()) p = "PC";
            o.put("Platform", p);
            o.put("DeviceId", normalizeDeviceId(d.deviceId));
            o.put("FriendlyName", d.friendlyName == null ? "" : d.friendlyName);
            o.put("Status", d.status);
            o.put("LinkedUtc", d.linkedUtc);
            o.put("UpdatedUtc", d.updatedUtc);
            ds.put(o);
        }
        root.put("Companies", cs);
        root.put("Devices", ds);
        return root;
    }

    LicenseDb deepCopy() throws JSONException {
        return fromJson(toJsonForEncryption());
    }

    void normalize() {
        normalize(false);
    }

    private void normalize(boolean migrateLegacyProducts) {
        dedupeProducts();
        companies.removeIf(c -> c == null || "TEST-NEODATTA".equals(c.id) || "TEST-EMPRESA".equals(c.id));

        for (Company c : companies) {
            String p = upper(c.profile);
            if (p.equals("ADMINISTRADOR")) c.profile = "NEODATTA";
            else if (p.equals("CLIENTE")) c.profile = "EMPRESA";
            else if (!p.equals("NEODATTA")) c.profile = "EMPRESA";

            if (isAdmin(c)) {
                c.productId = "*";
                c.softwareName = "ACESSO TOTAL";
                c.validUntil = null;
                c.deviceLimit = 0;
                c.accessAllProducts = true;
                c.deviceRequired = false;
            } else {
                c.accessAllProducts = false;
                c.deviceRequired = true;
                String sw = upper(c.softwareName);
                String pid = c.productId == null ? "" : c.productId.trim().toLowerCase(Locale.ROOT);
                Product byId = findProductById(pid);
                Product byName = findProductByName(sw);

                if (byName != null && (byId == null || !byName.productId.equals(byId.productId))) {
                    byId = byName;
                    pid = byName.productId;
                } else if (byId == null && !sw.isEmpty()) {
                    if (migrateLegacyProducts) {
                        Product migrated = addProductInternal(sw, pid);
                        byId = migrated;
                        pid = migrated.productId;
                    } else {
                        throw new IllegalArgumentException("O programa '" + sw + "' não está registrado. Use GERENCIAR PROGRAMAS.");
                    }
                }

                if (byId == null && !products.isEmpty()) {
                    Product preferred = findProductById("automation_utility");
                    byId = preferred != null ? preferred : products.get(0);
                    pid = byId.productId;
                }
                if (byId == null) throw new IllegalArgumentException("Nenhum programa está registrado.");

                c.productId = pid;
                c.softwareName = byId.name;
                c.deviceLimit = Math.max(0, c.deviceLimit);
            }
            c.status = upper(c.status);
            if (!c.status.equals("BLOCKED")) c.status = "ACTIVE";
        }

        java.util.HashSet<String> adminCompanyIds = new java.util.HashSet<>();
        for (Company c : companies) if (isAdmin(c)) adminCompanyIds.add(c.id == null ? "" : c.id.trim());

        Map<String, Device> best = new LinkedHashMap<>();
        for (Device d : new ArrayList<>(devices)) {
            if (d == null) continue;
            d.companyId = d.companyId == null ? "" : d.companyId.trim();
            if (adminCompanyIds.contains(d.companyId)) continue;
            d.deviceId = normalizeDeviceId(d.deviceId);
            if (d.companyId.isEmpty() || d.deviceId.isEmpty()) continue;
            d.platform = upper(d.platform);
            if (d.platform.isEmpty()) d.platform = platformFromDeviceId(d.deviceId);
            if (d.platform.isEmpty()) d.platform = "PC";
            d.status = upper(d.status);
            if (!d.status.equals("ACTIVE") && !d.status.equals("BLOCKED") && !d.status.equals("UNLINKED")) d.status = "ACTIVE";
            String key = d.companyId + "|" + d.deviceId;
            Device old = best.get(key);
            if (old == null || compareDevice(d, old) > 0) best.put(key, d);
        }
        devices.clear();
        devices.addAll(best.values());
    }

    private void ensureDefaultProducts() {
        ensureProduct("automation_utility", "AUTOMATION UTILITY");
        ensureProduct("mdgs_care", "MDGS CARE");
        ensureProduct("luna_astral", "LUNA ASTRAL");
        dedupeProducts();
    }

    private void ensureProduct(String id, String name) {
        if (findProductById(id) == null && findProductByName(name) == null) products.add(new Product(id, name));
    }

    private void dedupeProducts() {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        List<Product> cleaned = new ArrayList<>();
        for (Product p : products) {
            if (p == null) continue;
            String id = p.productId == null ? "" : p.productId.trim().toLowerCase(Locale.ROOT);
            String name = upper(p.name);
            if (id.isEmpty() || name.isEmpty() || "*".equals(id) || "ACESSO TOTAL".equals(name)) continue;
            if (ids.add(id) && names.add(name)) cleaned.add(new Product(id, name));
        }
        products.clear();
        products.addAll(cleaned);
    }

    Product addProduct(String name) {
        String n = upper(name);
        if (n.isEmpty()) throw new IllegalArgumentException("Informe o nome do programa.");
        if ("ACESSO TOTAL".equals(n)) throw new IllegalArgumentException("ACESSO TOTAL é reservado ao perfil ADMINISTRADOR.");
        if (findProductByName(n) != null) throw new IllegalArgumentException("Este programa já está registrado.");
        return addProductInternal(n, "");
    }

    private Product addProductInternal(String name, String preferredId) {
        String n = upper(name);
        Product existing = findProductByName(n);
        if (existing != null) return existing;
        String id = preferredId == null ? "" : preferredId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || "*".equals(id) || findProductById(id) != null) id = newProductId(n);
        Product p = new Product(id, n);
        products.add(p);
        dedupeProducts();
        Product resolved = findProductById(id);
        return resolved != null ? resolved : p;
    }

    void removeProduct(String productId) {
        String id = productId == null ? "" : productId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty() || "*".equals(id)) throw new IllegalArgumentException("Programa inválido.");
        for (Company c : companies) {
            if (!isAdmin(c) && id.equals((c.productId == null ? "" : c.productId.trim().toLowerCase(Locale.ROOT)))) {
                throw new IllegalArgumentException("Não é possível remover este programa porque há licença de cliente vinculada a ele.");
            }
        }
        products.removeIf(p -> id.equals(p.productId));
    }

    String newProductId(String name) {
        String normalized = Normalizer.normalize(upper(name), Normalizer.Form.NFD).replaceAll("\p{M}+", "");
        String base = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "programa";
        if (base.equals("automation_utility") || base.equals("mdgs_care") || base.equals("luna_astral")) return base;
        String id = base;
        int i = 2;
        while (findProductById(id) != null) id = base + "_" + i++;
        return id;
    }

    Product findProductById(String productId) {
        if (productId == null) return null;
        String id = productId.trim().toLowerCase(Locale.ROOT);
        for (Product p : products) if (id.equals(p.productId)) return p;
        return null;
    }

    Product findProductByName(String name) {
        String n = upper(name);
        for (Product p : products) if (n.equals(upper(p.name))) return p;
        return null;
    }

    private static int compareDevice(Device a, Device b) {
        long ta = timestamp(a.updatedUtc, a.linkedUtc);
        long tb = timestamp(b.updatedUtc, b.linkedUtc);
        if (ta != tb) return Long.compare(ta, tb);
        return Integer.compare(statusRank(a.status), statusRank(b.status));
    }

    private static int statusRank(String s) {
        s = upper(s);
        if (s.equals("ACTIVE")) return 3;
        if (s.equals("BLOCKED")) return 2;
        if (s.equals("UNLINKED")) return 1;
        return 0;
    }

    private static long timestamp(String primary, String fallback) {
        for (String v : new String[]{primary, fallback}) {
            try { if (v != null && !v.isEmpty()) return Instant.parse(v).toEpochMilli(); } catch (Exception ignored) {}
        }
        return 0L;
    }

    Company findCompany(String id) {
        if (id == null) return null;
        for (Company c : companies) if (id.equals(c.id)) return c;
        return null;
    }

    Device findDeviceById(String id) {
        if (id == null) return null;
        for (Device d : devices) if (id.equals(d.id)) return d;
        return null;
    }

    Device findDevice(String companyId, String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        for (Device d : devices) {
            if (companyId.equals(d.companyId) && normalized.equals(normalizeDeviceId(d.deviceId))) return d;
        }
        return null;
    }

    int usedDevices(String companyId, String excludeDeviceRecordId) {
        int n = 0;
        for (Device d : devices) {
            if (!companyId.equals(d.companyId)) continue;
            if (excludeDeviceRecordId != null && excludeDeviceRecordId.equals(d.id)) continue;
            String s = upper(d.status);
            if (s.equals("ACTIVE") || s.equals("BLOCKED")) n++;
        }
        return n;
    }

    static boolean isAdmin(Company c) {
        if (c == null) return false;
        String p = upper(c.profile);
        return p.equals("NEODATTA") || p.equals("ADMINISTRADOR");
    }

    static String uiProfile(Company c) {
        return isAdmin(c) ? "ADMINISTRADOR" : "CLIENTE";
    }

    static String storedProfile(String ui) {
        return "ADMINISTRADOR".equals(upper(ui)) ? "NEODATTA" : "EMPRESA";
    }

    static String normalizeDeviceId(String value) {
        String v = upper(value).trim();
        if (v.isEmpty()) return "";
        String compact = v.replaceAll("[^A-Z0-9]", "");
        if (compact.matches("DEVPC[A-F0-9]{16}")) return "DEV-PC-" + compact.substring(5);
        if (compact.matches("DEVANDROID[A-F0-9]{16}")) return "DEV-ANDROID-" + compact.substring(10);
        return v;
    }

    static String platformFromDeviceId(String value) {
        String v = normalizeDeviceId(value);
        if (v.startsWith("DEV-PC-")) return "PC";
        if (v.startsWith("DEV-ANDROID-")) return "ANDROID";
        return "";
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String upper(String v) {
        return v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
    }
}
