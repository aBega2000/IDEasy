package com.devonfw.tools.ide.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.devonfw.tools.ide.commandlet.Commandlet;
import com.devonfw.tools.ide.common.Tag;
import com.devonfw.tools.ide.common.Tags;
import com.devonfw.tools.ide.context.IdeContext;
import com.devonfw.tools.ide.environment.EnvironmentVariables;
import com.devonfw.tools.ide.environment.EnvironmentVariablesFiles;
import com.devonfw.tools.ide.nls.NlsBundle;
import com.devonfw.tools.ide.os.MacOsHelper;
import com.devonfw.tools.ide.process.EnvironmentContext;
import com.devonfw.tools.ide.process.ProcessContext;
import com.devonfw.tools.ide.process.ProcessErrorHandling;
import com.devonfw.tools.ide.process.ProcessMode;
import com.devonfw.tools.ide.property.StringProperty;
import com.devonfw.tools.ide.version.VersionIdentifier;

/**
 * {@link Commandlet} for a tool integrated into the IDE.
 */
public abstract class ToolCommandlet extends Commandlet implements Tags {

  /** @see #getName() */
  protected final String tool;

  private final Set<Tag> tags;

  /** The commandline arguments to pass to the tool. */
  public final StringProperty arguments;

  private MacOsHelper macOsHelper;

  /**
   * The constructor.
   *
   * @param context the {@link IdeContext}.
   * @param tool the {@link #getName() tool name}.
   * @param tags the {@link #getTags() tags} classifying the tool. Should be created via {@link Set#of(Object) Set.of} method.
   */
  public ToolCommandlet(IdeContext context, String tool, Set<Tag> tags) {

    super(context);
    this.tool = tool;
    this.tags = tags;
    addKeyword(tool);
    this.arguments = new StringProperty("", false, true, "args");
    initProperties();
  }

  /**
   * Add initial Properties to the tool
   */
  protected void initProperties() {

    add(this.arguments);
  }

  /**
   * @return the name of the tool (e.g. "java", "mvn", "npm", "node").
   */
  @Override
  public String getName() {

    return this.tool;
  }

  /**
   * @return the name of the binary executable for this tool.
   */
  protected String getBinaryName() {

    return this.tool;
  }

  @Override
  public final Set<Tag> getTags() {

    return this.tags;
  }

  @Override
  public void run() {

    runTool(ProcessMode.DEFAULT, null, this.arguments.asArray());
  }

  /**
   * Ensures the tool is installed and then runs this tool with the given arguments.
   *
   * @param processMode see {@link ProcessMode}
   * @param toolVersion the explicit version (pattern) to run. Typically {@code null} to ensure the configured version is installed and use that one.
   *     Otherwise, the specified version will be installed in the software repository without touching and IDE installation and used to run.
   * @param existsEnvironmentContext the {@link Boolean} that checks if an environment context exits. If true, then the process context is passed to the
   *     installation method as an argument.
   * @param args the command-line arguments to run the tool.
   */
  public void runTool(ProcessMode processMode, VersionIdentifier toolVersion, boolean existsEnvironmentContext, String... args) {

    Path binaryPath;
    binaryPath = Path.of(getBinaryName());
    ProcessContext pc;

    if (existsEnvironmentContext) {
      pc = createProcessContext(binaryPath, args);
      install(pc, true);
    } else {
      install(true);
      pc = createProcessContext(binaryPath, args);
    }

    pc.run(processMode);
  }

  /**
   * Creates a new {@link ProcessContext} from the given executable with the provided arguments attached.
   *
   * @param binaryPath path to the binary executable for this process
   * @param args the command-line arguments for this process
   * @return {@link ProcessContext}
   */
  protected ProcessContext createProcessContext(Path binaryPath, String... args) {

    return this.context.newProcess().errorHandling(ProcessErrorHandling.THROW).executable(binaryPath).addArgs(args);
  }

  /**
   * @param processMode see {@link ProcessMode}
   * @param toolVersion the explicit {@link VersionIdentifier} of the tool to run.
   * @param args the command-line arguments to run the tool.
   * @see ToolCommandlet#runTool(ProcessMode, VersionIdentifier, boolean, String...)
   */
  public void runTool(ProcessMode processMode, VersionIdentifier toolVersion, String... args) {

    runTool(processMode, toolVersion, false, args);
  }

  /**
   * @param toolVersion the explicit {@link VersionIdentifier} of the tool to run.
   * @param args the command-line arguments to run the tool.
   * @see ToolCommandlet#runTool(ProcessMode, VersionIdentifier, String...)
   */
  public void runTool(VersionIdentifier toolVersion, String... args) {

    runTool(ProcessMode.DEFAULT, toolVersion, args);
  }

  /**
   * @return the {@link EnvironmentVariables#getToolEdition(String) tool edition}.
   */
  public String getConfiguredEdition() {

    return this.context.getVariables().getToolEdition(getName());
  }

  /**
   * @return the {@link #getName() tool} with its {@link #getConfiguredEdition() edition}. The edition will be omitted if same as tool.
   * @see #getToolWithEdition(String, String)
   */
  protected final String getToolWithEdition() {

    return getToolWithEdition(getName(), getConfiguredEdition());
  }

  /**
   * @param tool the tool name.
   * @param edition the edition.
   * @return the {@link #getName() tool} with its {@link #getConfiguredEdition() edition}. The edition will be omitted if same as tool.
   */
  protected final static String getToolWithEdition(String tool, String edition) {

    if (tool.equals(edition)) {
      return tool;
    }
    return tool + "/" + edition;
  }

  /**
   * @return the {@link EnvironmentVariables#getToolVersion(String) tool version}.
   */
  public VersionIdentifier getConfiguredVersion() {

    return this.context.getVariables().getToolVersion(getName());
  }

  /**
   * Method to be called for {@link #install(boolean)} from dependent {@link com.devonfw.tools.ide.commandlet.Commandlet}s. Additionally, contains the
   * environmentContext of the tool
   *
   * @param environmentContext - the environment context that can be used for the dependencies
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and nothing has changed.
   */
  public boolean install(EnvironmentContext environmentContext) {

    return install(environmentContext, true);
  }

  /**
   * Method to be called for {@link #install(boolean)} from dependent {@link com.devonfw.tools.ide.commandlet.Commandlet}s.
   *
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and nothing has changed.
   */
  public boolean install() {

    return install(true);
  }

  /**
   * Performs the installation of the {@link #getName() tool} managed by this {@link com.devonfw.tools.ide.commandlet.Commandlet}. Additionally, contains the
   * environmentContext of the tool
   *
   * @param environmentContext - the environment context that can be used for the dependencies
   * @param silent - {@code true} if called recursively to suppress verbose logging, {@code false} otherwise.
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and nothing has changed.
   */
  public boolean install(EnvironmentContext environmentContext, boolean silent) {

    return doInstall(environmentContext, silent);
  }

  /**
   * Performs the installation of the {@link #getName() tool} managed by this {@link com.devonfw.tools.ide.commandlet.Commandlet}.
   *
   * @param silent - {@code true} if called recursively to suppress verbose logging, {@code false} otherwise.
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and nothing has changed.
   */
  public boolean install(boolean silent) {

    return doInstall(silent);
  }


  /**
   * Installs or updates the managed {@link #getName() tool}. Additionally works with the environment context of the tool.
   *
   * @param environmentContext - the environment context that can be used for the dependencies
   * @param silent - {@code true} if called recursively to suppress verbose logging, {@code false} otherwise.
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and nothing has changed.
   */
  protected abstract boolean doInstall(EnvironmentContext environmentContext, boolean silent);

  /**
   * Installs or updates the managed {@link #getName() tool}.
   *
   * @param silent - {@code true} if called recursively to suppress verbose logging, {@code false} otherwise.
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and nothing has changed.
   */
  protected abstract boolean doInstall(boolean silent);

  /**
   * This method is called after the tool has been newly installed or updated to a new version.
   */
  protected void postInstall() {

    // nothing to do by default
  }

  /**
   * @return {@code true} to extract (unpack) the downloaded binary file, {@code false} otherwise.
   */
  protected boolean isExtract() {

    return true;
  }

  /**
   * @return the {@link MacOsHelper} instance.
   */
  protected MacOsHelper getMacOsHelper() {

    if (this.macOsHelper == null) {
      this.macOsHelper = new MacOsHelper(this.context);
    }
    return this.macOsHelper;
  }

  /**
   * @return the currently installed {@link VersionIdentifier version} of this tool or {@code null} if not installed.
   */
  public abstract VersionIdentifier getInstalledVersion();

  /**
   * @return the installed edition of this tool or {@code null} if not installed.
   */
  public abstract String getInstalledEdition();

  /**
   * Uninstalls the {@link #getName() tool}.
   */
  public abstract void uninstall();

  /**
   * List the available editions of this tool.
   */
  public void listEditions() {

    List<String> editions = this.context.getUrls().getSortedEditions(getName());
    for (String edition : editions) {
      this.context.info(edition);
    }
  }

  /**
   * List the available versions of this tool.
   */
  public void listVersions() {

    List<VersionIdentifier> versions = this.context.getUrls().getSortedVersions(getName(), getConfiguredEdition());
    for (VersionIdentifier vi : versions) {
      this.context.info(vi.toString());
    }
  }

  /**
   * Sets the tool version in the environment variable configuration file.
   *
   * @param version the version (pattern) to set.
   */
  public void setVersion(String version) {

    if ((version == null) || version.isBlank()) {
      throw new IllegalStateException("Version has to be specified!");
    }
    VersionIdentifier configuredVersion = VersionIdentifier.of(version);
    if (!configuredVersion.isPattern() && !configuredVersion.isValid()) {
      this.context.warning("Version {} seems to be invalid", version);
    }
    setVersion(configuredVersion, true);
  }

  /**
   * Sets the tool version in the environment variable configuration file.
   *
   * @param version the version to set. May also be a {@link VersionIdentifier#isPattern() version pattern}.
   * @param hint - {@code true} to print the installation hint, {@code false} otherwise.
   */
  public void setVersion(VersionIdentifier version, boolean hint) {

    setVersion(version, hint, null);
  }

  /**
   * Sets the tool version in the environment variable configuration file.
   *
   * @param version the version to set. May also be a {@link VersionIdentifier#isPattern() version pattern}.
   * @param hint - {@code true} to print the installation hint, {@code false} otherwise.
   * @param destination - the destination for the property to be set
   */
  public void setVersion(VersionIdentifier version, boolean hint, EnvironmentVariablesFiles destination) {

    String edition = getConfiguredEdition();
    this.context.getUrls()
        .getVersionFolder(this.tool, edition, version); // CliException is thrown if the version is not existing

    EnvironmentVariables variables = this.context.getVariables();
    if (destination == null) {
      //use default location
      destination = EnvironmentVariablesFiles.SETTINGS;
    }
    EnvironmentVariables settingsVariables = variables.getByType(destination.toType());
    String name = EnvironmentVariables.getToolVersionVariable(this.tool);
    VersionIdentifier resolvedVersion = this.context.getUrls().getVersion(this.tool, edition, version);
    if (version.isPattern()) {
      this.context.debug("Resolved version {} to {} for tool {}/{}", version, resolvedVersion, this.tool, edition);
    }
    settingsVariables.set(name, resolvedVersion.toString(), false);
    settingsVariables.save();
    this.context.info("{}={} has been set in {}", name, version, settingsVariables.getSource());
    EnvironmentVariables declaringVariables = variables.findVariable(name);
    if ((declaringVariables != null) && (declaringVariables != settingsVariables)) {
      this.context.warning("The variable {} is overridden in {}. Please remove the overridden declaration in order to make the change affect.", name,
          declaringVariables.getSource());
    }
    if (hint) {
      this.context.info("To install that version call the following command:");
      this.context.info("ide install {}", this.tool);
    }
  }

  /**
   * Sets the tool edition in the environment variable configuration file.
   *
   * @param edition the edition to set.
   */
  public void setEdition(String edition) {

    setEdition(edition, true);
  }

  /**
   * Sets the tool edition in the environment variable configuration file.
   *
   * @param edition the edition to set
   * @param hint - {@code true} to print the installation hint, {@code false} otherwise.
   */
  public void setEdition(String edition, boolean hint) {

    setEdition(edition, hint, null);
  }

  /**
   * Sets the tool edition in the environment variable configuration file.
   *
   * @param edition the edition to set
   * @param hint - {@code true} to print the installation hint, {@code false} otherwise.
   * @param destination - the destination for the property to be set
   */
  public void setEdition(String edition, boolean hint, EnvironmentVariablesFiles destination) {

    if ((edition == null) || edition.isBlank()) {
      throw new IllegalStateException("Edition has to be specified!");
    }

    if (destination == null) {
      //use default location
      destination = EnvironmentVariablesFiles.SETTINGS;
    }
    if (!Files.exists(this.context.getUrls().getEdition(getName(), edition).getPath())) {
      this.context.warning("Edition {} seems to be invalid", edition);

    }
    EnvironmentVariables variables = this.context.getVariables();
    EnvironmentVariables settingsVariables = variables.getByType(destination.toType());
    String name = EnvironmentVariables.getToolEditionVariable(this.tool);
    settingsVariables.set(name, edition, false);
    settingsVariables.save();

    this.context.info("{}={} has been set in {}", name, edition, settingsVariables.getSource());
    EnvironmentVariables declaringVariables = variables.findVariable(name);
    if ((declaringVariables != null) && (declaringVariables != settingsVariables)) {
      this.context.warning("The variable {} is overridden in {}. Please remove the overridden declaration in order to make the change affect.", name,
          declaringVariables.getSource());
    }
    if (hint) {
      this.context.info("To install that edition call the following command:");
      this.context.info("ide install {}", this.tool);
    }
  }

  /**
   * Runs the tool's help command to provide the user with usage information.
   */
  @Override
  public void printHelp(NlsBundle bundle) {

    super.printHelp(bundle);
    String toolHelpArgs = getToolHelpArguments();
    if (toolHelpArgs != null && getInstalledVersion() != null) {
      ProcessContext pc = this.context.newProcess().errorHandling(ProcessErrorHandling.WARNING)
          .executable(Path.of(getBinaryName())).addArgs(toolHelpArgs);
      pc.run(ProcessMode.DEFAULT);
    }
  }

  /**
   * @return the tool's specific help command. Usually help, --help or -h. Return null if not applicable.
   */
  public String getToolHelpArguments() {

    return null;
  }
}
