using NUnit.Framework;

namespace Slothsoft.Jenkins.Unity.Integration.Tests {
    public sealed class IntegrationValueTest {
        [Test]
        public void ReturnsExpectedValue() {
            Assert.That(IntegrationValue.Answer, Is.EqualTo(42));
        }
    }
}
