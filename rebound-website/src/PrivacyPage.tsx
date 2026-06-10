export function PrivacyPage() {
  return (
    <div className="text-page">
      <h1>Privacy Policy</h1>
      <p className="updated">Last updated: June 11, 2026</p>

      <p>
        This policy describes what data the Quest: Rebound preset-sharing
        service (this website and its API) collects, how it is stored, and why.
        The Quest: Rebound mod itself works fully offline — data is only sent
        to this service when you explicitly use the preset sharing features
        (logging in, uploading, browsing, or reporting presets).
      </p>

      <h2>What we collect and why</h2>

      <h3>Account data</h3>
      <p>
        When you link your account from inside the mod, we verify your
        Minecraft session against Mojang's session servers and store your
        Minecraft UUID, your current Minecraft username, the time you first
        linked, and the time of your last verification. This is used to
        attribute uploaded presets to their author, to display author names in
        the preset browser, and to authenticate your requests. We never see or
        store your Microsoft/Mojang password or email address.
      </p>

      <h3>Login challenges</h3>
      <p>
        During login, a short-lived random challenge is stored together with
        the IP address that requested it. This exists solely to prevent abuse
        of the login endpoint. Challenges expire after a few minutes and
        expired entries are deleted automatically.
      </p>

      <h3>Presets</h3>
      <p>
        When you upload a preset, we store the title, description, Minecraft
        version, mod loader, mod version, the preset's keybinding files, and
        the upload time, linked to your Minecraft UUID. This data is public:
        anyone can browse, search, and download shared presets — that is the
        purpose of the service. Do not include personal information in preset
        titles, descriptions, or files.
      </p>

      <h3>Reports</h3>
      <p>
        If you report a preset, we store the reported preset, your Minecraft
        UUID, the reason, and optional details, so we can moderate shared
        content.
      </p>

      <h3>Technical data</h3>
      <p>
        Like most web servers, the service applies rate limiting based on IP
        addresses to protect against abuse. We do not use cookies, analytics,
        trackers, or advertising of any kind. The Modrinth badge image on the
        homepage is loaded from the jsDelivr CDN, which may see your IP
        address as part of serving that image (see jsDelivr's own privacy
        policy).
      </p>

      <h2>How data is stored</h2>
      <p>
        All data is stored in a database on the server that hosts this
        service. Authentication uses signed tokens stored only on your own
        device; the server does not keep session records beyond the account
        data described above. Data is kept until you delete it or until the
        service no longer needs it.
      </p>

      <h2>Your rights</h2>
      <p>
        You can request deletion of your account data and any presets or
        reports linked to your Minecraft UUID at any time. Contact us at{' '}
        <a href="mailto:florian.reintgen@gmail.com">
          florian.reintgen@gmail.com
        </a>{' '}
        — include your Minecraft username or UUID so we can find your data.
        You may also request a copy of the data stored about you.
      </p>

      <h2>Changes</h2>
      <p>
        If this policy changes, the updated version will be published on this
        page with a new "last updated" date.
      </p>
    </div>
  )
}
