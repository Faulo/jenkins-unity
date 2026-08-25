using System;
using System.Linq;
using Microsoft.Unity.VisualStudio.Editor;

namespace Slothsoft.UnityExtensions.Editor {
    public static class Build {
        const string ProjectGeneratorName = "LegacyStyleProjectGeneration";

        public static void Solution() {
            var generatorType = typeof(ProjectGeneration)
                .Assembly
                .GetTypes()
                .First(type => type.Name == ProjectGeneratorName);
            var generator = Activator.CreateInstance(generatorType) as IGenerator;
            generator.Sync();
        }
    }
}
