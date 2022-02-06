package com.hedera.services.stream;

import com.hedera.services.context.properties.SemanticVersions;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CurrentHederaStreamType implements HederaStreamType {
	private final SemanticVersions semanticVersions;

	private int[] hapiProtoVersionHeader = null;

	@Inject
	public CurrentHederaStreamType(final SemanticVersions semanticVersions) {
		this.semanticVersions = semanticVersions;
	}

	@Override
	public int[] getFileHeader() {
		if (hapiProtoVersionHeader == null) {
			final var deployed = semanticVersions.getDeployed();
			final var protoSemVer = deployed.protoSemVer();
			hapiProtoVersionHeader = new int[] {
					protoSemVer.getMajor(),
					protoSemVer.getMinor(),
					protoSemVer.getPatch()
			};
		}
		return hapiProtoVersionHeader;
	}
}
