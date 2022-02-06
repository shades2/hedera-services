package com.hedera.services.stream;

import com.hedera.services.context.properties.ActiveVersions;
import com.hedera.services.context.properties.SemanticVersions;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CurrentHederaStreamTypeTest {
	private static final SemanticVersion pretendSemVer = SemanticVersion.newBuilder()
			.setMajor(1)
			.setMinor(2)
			.setPatch(4)
			.setPre("zeta.123")
			.setBuild("2b26be40")
			.build();
	private static final int[] expectedHeader = new int[] {
			pretendSemVer.getMajor(), pretendSemVer.getMinor(), pretendSemVer.getPatch()
	};

	@Mock
	private ActiveVersions activeVersions;
	@Mock
	private SemanticVersions semanticVersions;

	private CurrentHederaStreamType subject;

	@BeforeEach
	void setUp() {
		subject = new CurrentHederaStreamType(semanticVersions);
	}

	@Test
	void returnsCurrentStreamTypeFromResource() {
		given(semanticVersions.getDeployed()).willReturn(activeVersions);
		given(activeVersions.protoSemVer()).willReturn(pretendSemVer);

		final var header = subject.getFileHeader();
		assertArrayEquals(expectedHeader, header);
		assertSame(header, subject.getFileHeader());
	}
}