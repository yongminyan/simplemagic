package com.j256.simplemagic.entries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.simplemagic.ContentType;
import com.j256.simplemagic.endian.EndianConverter;

/**
 * Representation of a line of information from the magic (5) format.
 * 
 * @author graywatson
 */
public class MagicEntry {

	private static final String UNKNOWN_NAME = "unknown";

	private List<MagicEntry> children;

	private final MagicEntry parent;
	private final String name;
	private final int level;
	private final boolean addOffset;
	private final int offset;
	private final OffsetInfo offsetInfo;
	private final MagicMatcher matcher;
	private final Long andValue;
	private final boolean unsignedType;
	// the testValue object is defined by the particular matcher
	private final Object testValue;
	private final boolean formatSpacePrefix;
	private final Formatter formatter;

	private int strength;
	private String mimeType;
	private Map<String, String> extensionMap;

	/**
	 * Package protected constructor.
	 */
	MagicEntry(MagicEntry parent, String name, int level, boolean addOffset, int offset, OffsetInfo offsetInfo,
			MagicMatcher matcher, Long andValue, boolean unsignedType, Object testValue, boolean formatSpacePrefix,
			String format) {
		this.parent = parent;
		this.name = name;
		this.level = level;
		this.addOffset = addOffset;
		this.offset = offset;
		this.offsetInfo = offsetInfo;
		this.matcher = matcher;
		this.andValue = andValue;
		this.unsignedType = unsignedType;
		this.testValue = testValue;
		this.formatSpacePrefix = formatSpacePrefix;
		if (format == null) {
			this.formatter = null;
		} else {
			this.formatter = new Formatter(format);
		}
		this.strength = 1;
	}

	/**
	 * Returns the content type associated with the bytes or null if it does not match.
	 */
	public ContentType processBytes(byte[] bytes) {
		ContentInfo info = processBytes(bytes, 0, null);
		if (info == null || info.name == UNKNOWN_NAME) {
			return null;
		} else {
			return new ContentType(info.name, info.mimeType, info.sb.toString());
		}
	}

	/**
	 * Return the "level" of the rule. Level-0 rules start the matching process. Level-1 and above rules are processed
	 * only when the level-0 matches.
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Get the strength of the rule. Not well supported right now.
	 */
	public int getStrength() {
		return strength;
	}

	void setStrength(int strength) {
		this.strength = strength;
	}

	MagicEntry getParent() {
		return parent;
	}

	void addChild(MagicEntry child) {
		if (children == null) {
			children = new ArrayList<MagicEntry>();
		}
		children.add(child);
	}

	void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("level ").append(level);
		if (name != null) {
			sb.append(",name '").append(name).append('\'');
		}
		if (mimeType != null) {
			sb.append(",mime '").append(mimeType).append('\'');
		}
		if (testValue != null) {
			sb.append(",test '").append(testValue).append('\'');
		}
		if (formatter != null) {
			sb.append(",format '").append(formatter).append('\'');
		}
		return sb.toString();
	}

	private ContentInfo processBytes(byte[] bytes, int prevOffset, ContentInfo contentInfo) {
		int offset = this.offset;
		if (offsetInfo != null) {
			offset = offsetInfo.getOffset(bytes);
		}
		if (addOffset) {
			offset = prevOffset + offset;
		}
		Object val = matcher.extractValueFromBytes(offset, bytes);
		if (val == null) {
			return null;
		}
		if (testValue != null) {
			val = matcher.isMatch(testValue, andValue, unsignedType, val, offset, bytes);
			if (val == null) {
				return null;
			}
		}

		if (contentInfo == null) {
			contentInfo = new ContentInfo(name, mimeType);
		}
		if (formatter != null) {
			// if we are appending and need a space then preprend one
			if (formatSpacePrefix && contentInfo.sb.length() > 0) {
				contentInfo.sb.append(' ');
			}
			matcher.renderValue(contentInfo.sb, val, formatter);
		}
		if (children != null) {
			/*
			 * If there are children then one of them has to match otherwise the whole thing doesn't match. This is
			 * necessary for formats that are XML but we don't want to dominate plain old XML documents.
			 */
			boolean matched = false;
			// we have to do this because the children's children set the name first otherwise
			boolean assignName = (contentInfo.name == UNKNOWN_NAME);
			for (MagicEntry child : children) {
				if (child.processBytes(bytes, offset, contentInfo) != null) {
					matched = true;
					if (assignName) {
						contentInfo.setName(child);
					}
					if (contentInfo.mimeType == null && child.mimeType != null) {
						contentInfo.mimeType = child.mimeType;
					}
				}
			}
			if (!matched) {
				return null;
			}
		}
		return contentInfo;
	}

	void addExtension(String key, String value) {
		if (extensionMap == null) {
			extensionMap = new HashMap<String, String>();
		}
		extensionMap.put(key, value);
	}

	static class ContentInfo {
		String name;
		int nameLevel;
		String mimeType;
		final StringBuilder sb = new StringBuilder();
		private ContentInfo(String name, String mimeType) {
			this.name = name;
			this.mimeType = mimeType;
		}
		public void setName(MagicEntry entry) {
			if (name == UNKNOWN_NAME || (entry.name != null && entry.name != UNKNOWN_NAME && entry.level < nameLevel)) {
				name = entry.name;
				nameLevel = entry.level;
			}
		}
	}

	/**
	 * Information about the extended offset.
	 */
	static class OffsetInfo {

		final int offset;
		final EndianConverter converter;
		final boolean id3;
		final int size;
		final int add;

		OffsetInfo(int offset, EndianConverter converter, boolean id3, int size, int add) {
			this.offset = offset;
			this.converter = converter;
			this.id3 = id3;
			this.size = size;
			this.add = add;
		}

		public Integer getOffset(byte[] bytes) {
			Long val;
			if (id3) {
				val = (Long) converter.convertId3(offset, bytes, size);
			} else {
				val = (Long) converter.convertNumber(offset, bytes, size);
			}
			if (val == null) {
				return null;
			} else {
				return (int) (val + add);
			}
		}
	}
}
