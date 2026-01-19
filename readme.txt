• Added a small loader that reads settings/mcp.json
  from an override dir, the install area, or the
  working directory and wires it into startup; the
  product build now copies settings/mcp.json into
  each generated product so it’s available at
  runtime. The loader logs the source and byte
  count (not the contents) to avoid leaking
  secrets.

  Changes:

  - com.kk.pde.ds.app/src/com/kk/pde/ds/app/
    McpSettings.java loads the file from
    -Dmcp.settings.dir, osgi.install.area, or ./
    settings/mcp.json.
  - com.kk.pde.ds.app/src/com/kk/pde/ds/app/
    App.java calls the loader at activation.
  - distribution/pom.xml copies settings/mcp.json
    into each product root settings/ directory
    during verify.

  How to use while debugging:

  - Set a VM arg in your run config:
    -Dmcp.settings.dir=D:\Dev\p.osgi.ds\settings
  - Or set the working directory to D:
    \Dev\p.osgi.ds so ./settings/mcp.json resolves.

  How to use in products:

  - After building, the file will be at <product>/
    settings/mcp.json and is found via
    osgi.install.area.
    
    
     Here are the common ways to set the working
  directory so ./settings/mcp.json resolves:

  Eclipse (PDE launch)

  - Run > Run Configurations…
  - Select your launch (e.g., “OSGi Framework” or
    “Eclipse Application”)
  - On the Main tab, set Working directory to Other
    and browse to D:\Dev\p.osgi.ds
  - Apply/Run

  IntelliJ

  - Run > Edit Configurations…
  - Pick your configuration
  - Set Working directory to D:\Dev\p.osgi.ds
  - Apply/Run

  Command line

  Set-Location D:\Dev\p.osgi.ds
  # then run your launch command from here

  If you tell me how you’re launching (Eclipse,
  IntelliJ, CLI, etc.), I can give exact clicks/
  commands.