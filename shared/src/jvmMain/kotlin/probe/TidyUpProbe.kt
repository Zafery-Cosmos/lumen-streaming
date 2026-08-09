package probe

import app.lumen.ui.settings.tidyUp

/**
 * Vérifie le redressement de la saisie d'un bucket sur des cas réels.
 *
 * Cette fonction ne se teste pas à l'œil : elle enchaîne des règles qui
 * interagissent (le fournisseur détecté commande la région déduite, le chemin
 * retiré de l'adresse alimente le bucket). Les cas ci-dessous viennent de
 * pages de fournisseurs, pas d'exemples inventés.
 */
private var failures = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    if (condition) {
        println("  ok   $name")
    } else {
        failures++
        println("  FAIL $name ${if (detail.isEmpty()) "" else "→ $detail"}")
    }
}

fun main() {
    println("Backblaze, clés interverties")
    tidyUp(
        endpoint = "https://s3.us-east-005.backblazeb2.com",
        region = "us-east-005", bucket = "seenzo",
        accessKey = "K005zDzJ1sFODKZbV2PQDnwaQtty0P4",
        secretKey = "005a3276739125e0000000003",
        kind = "s3",
    ).let {
        check("access = keyID", it.accessKey == "005a3276739125e0000000003", it.accessKey)
        check("secret = applicationKey", it.secretKey == "K005zDzJ1sFODKZbV2PQDnwaQtty0P4", it.secretKey)
        check("fournisseur reconnu b2", it.kind == "b2", it.kind)
        check("interversion annoncée", it.notes.any { n -> n.contains("inversées") }, it.notes.toString())
    }

    println("Backblaze, saisie déjà correcte : ne rien toucher")
    tidyUp(
        "https://s3.us-east-005.backblazeb2.com", "us-east-005", "seenzo",
        "005a3276739125e0000000003", "K005zDzJ1sFODKZbV2PQDnwaQtty0P4", "b2",
    ).let {
        check("access inchangée", it.accessKey == "005a3276739125e0000000003", it.accessKey)
        check("secret inchangée", it.secretKey == "K005zDzJ1sFODKZbV2PQDnwaQtty0P4", it.secretKey)
        check("aucune correction", it.notes.isEmpty(), it.notes.toString())
    }

    println("Adresse nue, région vide, espaces de collage")
    tidyUp("  s3.us-west-004.backblazeb2.com ", "", " seenzo ", " abc ", " def ", "s3").let {
        check("https ajouté", it.endpoint == "https://s3.us-west-004.backblazeb2.com", it.endpoint)
        check("région déduite", it.region == "us-west-004", it.region)
        check("bucket détouré", it.bucket == "seenzo", it.bucket)
        check("clés détourées", it.accessKey == "abc" && it.secretKey == "def")
        check("pas d'interversion", it.notes.none { n -> n.contains("inversées") }, it.notes.toString())
    }

    println("Chemin du bucket collé à l'adresse")
    tidyUp("https://s3.eu-west-3.amazonaws.com/films/", "", "", "AKIA0000", "s", "s3").let {
        check("adresse coupée", it.endpoint == "https://s3.eu-west-3.amazonaws.com", it.endpoint)
        check("bucket repris", it.bucket == "films", it.bucket)
        check("région déduite", it.region == "eu-west-3", it.region)
        check("fournisseur s3", it.kind == "s3", it.kind)
    }

    println("Cloudflare R2 : secret deux fois plus long, interverti")
    val r2Id = "a".repeat(32)
    val r2Secret = "b".repeat(64)
    tidyUp("https://abc123.r2.cloudflarestorage.com", "", "medias", r2Secret, r2Id, "s3").let {
        check("clés remises d'aplomb", it.accessKey == r2Id && it.secretKey == r2Secret)
        check("région auto", it.region == "auto", it.region)
        check("fournisseur r2", it.kind == "r2", it.kind)
    }

    println("Chemin collé mais bucket déjà rempli : ne pas l'écraser")
    tidyUp("https://s3.eu-west-3.amazonaws.com/autre", "", "films", "AKIA0000", "s", "s3").let {
        check("bucket conservé", it.bucket == "films", it.bucket)
        check("chemin signalé", it.notes.any { n -> n.contains("Chemin retiré") }, it.notes.toString())
    }

    println("MinIO auto-hébergé : aucune règle ne doit s'appliquer de travers")
    tidyUp("https://minio.chezmoi.lan:9000", "", "films", "admin", "motdepasse", "s3").let {
        check("adresse intacte", it.endpoint == "https://minio.chezmoi.lan:9000", it.endpoint)
        check("région laissée vide", it.region.isEmpty(), it.region)
        check("fournisseur inchangé", it.kind == "s3", it.kind)
        check("clés intactes", it.accessKey == "admin" && it.secretKey == "motdepasse")
    }

    println()
    if (failures == 0) println("TOUT PASSE") else println("$failures ÉCHEC(S)")
    kotlin.system.exitProcess(if (failures == 0) 0 else 1)
}
