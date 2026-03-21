{
  description = "DroidClaw Android development shell";

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
            jdk21
            gradle
            gh
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.jdk21}/lib/openjdk"
            export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME ''${GRADLE_OPTS:-}"
            echo "DroidClaw dev shell"
            echo "JAVA_HOME=$JAVA_HOME"
            java -version
          '';
        };
      });
    };
}