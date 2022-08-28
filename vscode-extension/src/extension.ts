import { commands, ExtensionContext, window, workspace } from "vscode";
import { LanguageClient } from "vscode-languageclient/node";

export function activate(context: ExtensionContext) {
  const serverArtifact = workspace
    .getConfiguration()
    .get<string>("smithyql.server.artifact");

  const serverVersion = workspace
    .getConfiguration()
    .get<string>("smithyql.server.version");

  const coursierTTL = serverVersion === "latest.integration" ? "0" : "1h";

  const outputChannel = window.createOutputChannel(
    "Smithy Playground",
    "smithyql"
  );

  const enableTracer = workspace
    .getConfiguration()
    .get<boolean>("smithyql.server.trace");

  const enableDebug = workspace
    .getConfiguration()
    .get<boolean>("smithyql.server.debug");

  const tracerArgs = enableTracer
    ? [
        "tech.neander:langoustine-tracer_3:latest.release",
        "--", //separator for coursier launch
        "--", //separator for tracer
        "cs",
        "launch",
      ]
    : [];

  const debugArgs = enableDebug
    ? [
        "--java-opt",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,quiet=y,address=5010",
      ]
    : [];

  const lspClient = new LanguageClient(
    "smithyPlayground",
    "Smithy Playground",
    {
      command: "cs",
      args: [
        "launch",
        ...tracerArgs,
        `${serverArtifact}:${serverVersion}`,
        "--ttl",
        coursierTTL,
        ...debugArgs,
      ],
    },
    {
      documentSelector: [{ language: "smithyql" }],
      synchronize: {
        fileEvents: workspace.createFileSystemWatcher(
          "**/{build/smithy-dependencies.json,.smithy.json,smithy-build.json}"
        ),
      },
      outputChannel,
    }
  );

  const registerRunCommand = commands.registerTextEditorCommand(
    "smithyql.runQuery",
    (editor) => {
      lspClient.sendRequest("smithyql/runQuery", {
        uri: editor.document.uri.toString(),
      });
    }
  );

  const registerOutputPanelNotification = lspClient.onNotification(
    "smithyql/showOutputPanel",
    (_) => lspClient.outputChannel.show(true)
  );

  lspClient.start();

  context.subscriptions.push(
    lspClient,
    registerRunCommand,
    registerOutputPanelNotification
  );
}
