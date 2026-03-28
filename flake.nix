{
  description = "DroidClaw Android development shell with Python support";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f:
        nixpkgs.lib.genAttrs systems (system:
          f {
            pkgs = import nixpkgs { inherit system; };
          });
    in
    {
      devShells = forAllSystems ({ pkgs }: {
        default = pkgs.mkShell {
          packages = with pkgs; [
            # Java/Android
            jdk21
            gradle
            gh

            # Python for Chaquopy build
            python311

            # Build tools
            unzip
            which
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk21}/lib/openjdk"
            export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME ''${GRADLE_OPTS:-}"

            # Python for Chaquopy
            export PYTHON="${pkgs.python311}/bin/python3.11"
            export PYTHON_VERSION="3.11"

            # Aliases for common tasks
            alias build-app="./gradlew assembleDebug"
            alias install-app="./gradlew installDebug"
            # alias test="./gradlew test"
            alias test-unit-app="./gradlew testDebugUnitTest"
            alias test-instrumented-app="./gradlew connectedAndroidTest"
            alias lint-app="./gradlew lintDebug"
            alias clean-app="./gradlew clean"

            echo "DroidClaw dev shell with Python support"
            echo "JAVA_HOME=$JAVA_HOME"
            echo "PYTHON=$PYTHON"
            java -version
            python3 --version
            echo ""
            echo "Available aliases: build-app, install-app, test-unit-app, test-instrumented-app, lint-app, clean-app"
          '';
        };
      });
    };
}