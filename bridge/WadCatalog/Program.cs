using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using Imcodec.IO;
using Imcodec.ObjectProperty;
using Imcodec.ObjectProperty.TypeCache;
using Imcodec.Wad;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;

// This program only opens source files for reading. Game processes are never accessed.
if (args.Length is < 2 or > 3 || args.Length == 3 && args[2] != "--resources-only")
    throw new ArgumentException("Usage: WadCatalog <client-directory> <new-output-directory> [--resources-only]");
var resourcesOnly = args.Length == 3;
var client = Path.GetFullPath(args[0]);
var output = Path.GetFullPath(args[1]);
if (output.Equals(client, StringComparison.OrdinalIgnoreCase) ||
    output.StartsWith(Path.TrimEndingDirectorySeparator(client) + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
    throw new IOException("Choose an output outside the source client directory.");
if (Directory.Exists(output)) throw new IOException("Output must be a new directory (preserves previous captures).");
Directory.CreateDirectory(output);
var settings = new JsonSerializerSettings {
    TypeNameHandling = TypeNameHandling.Auto,
    Converters = { new ByteStringConverter(), new StringEnumConverter() }
};
var streams = new Dictionary<string, FileStream>();
var archives = new Dictionary<string, Archive>();
var provenance = new Dictionary<string, object>();
var started = DateTimeOffset.UtcNow;
var executablePath = Path.Combine(client, "Bin", "WizardGraphicalClient.exe");
var executableHash = Convert.ToHexString(SHA256.HashData(File.ReadAllBytes(executablePath))).ToLowerInvariant();
string Json(object value) => JsonConvert.SerializeObject(value, settings);
string Hash(byte[] bytes) => Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
Archive Open(string name) {
    if (archives.TryGetValue(name, out var cached)) return cached;
    var path = Path.Combine(client, "Data", "GameData", name + ".wad");
    // Deny writes while decoding this archive so patching cannot mix revisions.
    var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
    streams.Add(name, stream);
    var digest = Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
    stream.Position = 0;
    var archive = ArchiveParser.Parse(stream) ?? throw new IOException("Invalid WAD: " + path);
    archives.Add(name, archive);
    provenance.Add(name, new { path, sha256 = digest, size = stream.Length, members = archive.FileCount });
    return archive;
}
byte[] Read(string archive, string member) {
    var wad = Open(archive);
    var bytes = (wad.OpenFile(member) ?? throw new FileNotFoundException($"{archive}: {member}")).ToArray();
    var entry = wad.Files[member].Value;
    if (bytes.Length != entry.UncompressedSize) throw new IOException("Member length mismatch: " + member);
    // The retail header checksum does not match standard CRC32 for either the
    // compressed or plain manifest. Its semantics are unverified; provenance
    // uses SHA256 of the archive and uncompressed members instead.
    return bytes;
}
PropertyClass Decode(byte[] bytes) {
    if (!new BindSerializer().Deserialize<PropertyClass>(bytes, 7, out var obj) || obj is null)
        throw new InvalidDataException("Imcodec returned no object");
    return obj;
}
using var templates = new StreamWriter(Path.Combine(output, "templates.ndjson"), false, new UTF8Encoding(false));
using var resources = new StreamWriter(Path.Combine(output, "resources.ndjson"), false, new UTF8Encoding(false));
using var coverage = new StreamWriter(Path.Combine(output, "coverage.ndjson"), false, new UTF8Encoding(false));
int decoded = 0, items = 0, failed = 0;
var types = new Dictionary<string, int>();
try {
    var root = Open("Root");
    var manifestBytes = Read("Root", "TemplateManifest.xml");
    File.WriteAllBytes(Path.Combine(output, "TemplateManifest.bin"), manifestBytes);
    var manifest = Decode(manifestBytes) as TemplateManifest ?? throw new InvalidDataException("Manifest type mismatch");
    resources.WriteLine(Json(new { archive = "Root", member = "TemplateManifest.xml", sha256 = Hash(manifestBytes), decoded = manifest }));
    foreach (var member in root.Files.Keys.Order(StringComparer.Ordinal)) {
        if (!(member.StartsWith("GameEffectData/") || member.StartsWith("GameEffectRuleData/") ||
              member.StartsWith("MagicSchool") || member.Contains("SetBonus", StringComparison.OrdinalIgnoreCase) ||
              member.Contains("Talent", StringComparison.OrdinalIgnoreCase) && member.EndsWith(".xml") ||
              member.StartsWith("Locale/en-US/") && member.EndsWith(".lang"))) continue;
        try {
            var bytes = Read("Root", member);
            object data = member.EndsWith(".lang") ? Encoding.Unicode.GetString(bytes) : Decode(bytes);
            resources.WriteLine(Json(new { archive = "Root", member, sha256 = Hash(bytes), decoded = data }));
        } catch (Exception e) {
            resources.WriteLine(Json(new { archive = "Root", member, error = e.GetType().Name + ": " + e.Message }));
        }
    }
    Console.WriteLine($"Manifest: {manifest.m_serializedTemplates.Count} templates; resources captured.");
    var ids = new HashSet<string>();
    foreach (var location in manifest.m_serializedTemplates) {
        if (resourcesOnly) break;
        var id = location.m_id.ToString(CultureInfo.InvariantCulture);
        if (!ids.Add(id)) throw new InvalidDataException("Duplicate manifest ID: " + id);
        var path = location.m_filename.ToString();
        var archive = "Root";
        var member = path;
        if (path.StartsWith('|')) {
            var parts = path.Split('|', 4);
            if (parts.Length != 4 || parts[2] != "WorldData") {
                coverage.WriteLine(Json(new { id, path, status = "unsupported-location" }));
                failed++;
                continue;
            }
            archive = parts[1] + "-WorldData";
            member = parts[3];
        }
        try {
            var bytes = Read(archive, member);
            var obj = Decode(bytes);
            var type = obj.GetType().Name;
            types[type] = types.GetValueOrDefault(type) + 1;
            decoded++;
            if (obj is WizItemTemplate) {
                items++;
                templates.WriteLine(Json(new { id, archive, member, sha256 = Hash(bytes), type, decoded = obj }));
            }
            coverage.WriteLine(Json(new { id, archive, member, type, status = "decoded" }));
        } catch (Exception e) {
            failed++;
            coverage.WriteLine(Json(new { id, archive, member, status = "failed", error = e.GetType().Name + ": " + e.Message }));
        }
        if ((decoded + failed) % 10000 == 0) Console.WriteLine($"Processed {decoded + failed}; items {items}; failures {failed}");
    }
    if (Hash(File.ReadAllBytes(executablePath)) != executableHash)
        throw new IOException("Client executable changed during capture; use a stable client snapshot.");
    var decoderPath = typeof(BindSerializer).Assembly.Location;
    File.WriteAllText(Path.Combine(output, "capture.json"), Json(new {
        schemaVersion = 1, startedAt = started, finishedAt = DateTimeOffset.UtcNow,
        client, executableSha256 = executableHash,
        decoder = new { path = decoderPath, sha256 = Hash(File.ReadAllBytes(decoderPath)), version = typeof(BindSerializer).Assembly.GetName().Version?.ToString() },
        archives = provenance, resourcesOnly, discovered = resourcesOnly ? 0 : manifest.m_serializedTemplates.Count, decoded, items, failed, types,
        validation = "Decoded with local Imcodec schema; retail item-card verification pending. Availability unknown."
    }) + "\n");
    Console.WriteLine($"Finished: {decoded} decoded, {items} item templates, {failed} failures.");
} finally {
    foreach (var stream in streams.Values) stream.Dispose();
}

sealed class ByteStringConverter : JsonConverter {
    public override bool CanConvert(System.Type type) => type == typeof(ByteString);
    public override void WriteJson(JsonWriter writer, object? value, JsonSerializer serializer) => writer.WriteValue(value?.ToString());
    public override object ReadJson(JsonReader reader, System.Type type, object? existing, JsonSerializer serializer) => throw new NotSupportedException();
}
