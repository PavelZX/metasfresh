package de.metas.ui.web.dashboard;

import java.time.Duration;
import java.util.List;

import org.adempiere.ad.expression.api.IStringExpression;
import org.adempiere.ad.expression.api.impl.StringExpressionCompiler;
import org.adempiere.util.Check;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import de.metas.i18n.ITranslatableString;
import de.metas.i18n.ImmutableTranslatableString;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class KPI
{
	public static final Builder builder()
	{
		return new Builder();
	}

	private final int id;
	private final ITranslatableString caption;
	private final ITranslatableString description;
	private final KPIChartType chartType;
	private final Duration compareOffset;
	private final Duration defaultTimeRange;

	private final List<KPIField> fields;
	private final KPIField groupByField;

	private final String esSearchIndex;
	private final String esSearchTypes;
	private final IStringExpression esQuery;

	private final int pollIntervalSec;

	private KPI(final Builder builder)
	{
		super();

		Check.assume(builder.id > 0, "id > 0");
		Check.assumeNotNull(builder.caption, "Parameter builder.caption is not null");
		Check.assumeNotNull(builder.description, "Parameter builder.description is not null");
		Check.assumeNotNull(builder.chartType, "Parameter builder.chartType is not null");
		Check.assumeNotEmpty(builder.fields, "builder.fields is not empty");
		Check.assumeNotEmpty(builder.esSearchIndex, "builder.esSearchIndex is not empty");
		Check.assumeNotEmpty(builder.esSearchTypes, "builder.esSearchTypes is not empty");
		Check.assumeNotEmpty(builder.esQuery, "builder.esQuery is not empty");

		id = builder.id;

		caption = builder.caption;
		description = builder.description;
		chartType = builder.chartType;
		compareOffset = builder.compareOffset;
		defaultTimeRange = builder.defaultTimeRange;

		fields = ImmutableList.copyOf(builder.fields);
		groupByField = fields.stream()
				.filter(KPIField::isGroupBy)
				.findFirst()
				.orElse(null);

		esSearchIndex = builder.esSearchIndex;
		esSearchTypes = builder.esSearchTypes;
		esQuery = StringExpressionCompiler.instance.compile(builder.esQuery);

		pollIntervalSec = builder.pollIntervalSec;
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("id", id)
				.add("caption", caption.getDefaultValue())
				.toString();
	}

	public int getId()
	{
		return id;
	}

	public String getCaption(final String adLanguage)
	{
		return caption.translate(adLanguage);
	}

	public String getDescription(final String adLanguage)
	{
		return description.translate(adLanguage);
	}

	public KPIChartType getChartType()
	{
		return chartType;
	}

	public List<KPIField> getFields()
	{
		return fields;
	}

	public KPIField getGroupByField()
	{
		if (groupByField == null)
		{
			throw new IllegalStateException("KPI has no group by field defined");
		}
		return groupByField;
	}

	public Duration getDefaultTimeRange()
	{
		return defaultTimeRange;
	}

	public boolean hasCompareOffset()
	{
		return compareOffset != null;
	}

	public Duration getCompareOffset()
	{
		return compareOffset;
	}

	public int getPollIntervalSec()
	{
		return pollIntervalSec;
	}

	public IStringExpression getESQuery()
	{
		return esQuery;
	}

	public String getESSearchIndex()
	{
		return esSearchIndex;
	}

	public String getESSearchTypes()
	{
		return esSearchTypes;
	}

	public static final class Builder
	{
		private int id;
		private ITranslatableString caption = ImmutableTranslatableString.empty();
		private ITranslatableString description = ImmutableTranslatableString.empty();
		private KPIChartType chartType;
		private Duration compareOffset;
		private Duration defaultTimeRange = Duration.ZERO;
		private List<KPIField> fields;

		private String esSearchTypes;
		private String esSearchIndex;
		private String esQuery;
		private int pollIntervalSec;

		private Builder()
		{
			super();
		}

		public KPI build()
		{
			return new KPI(this);
		}

		public Builder setId(final int id)
		{
			this.id = id;
			return this;
		}

		public Builder setCaption(final ITranslatableString caption)
		{
			this.caption = caption;
			return this;
		}

		public Builder setDescription(final ITranslatableString description)
		{
			this.description = description;
			return this;
		}

		public Builder setChartType(final KPIChartType chartType)
		{
			this.chartType = chartType;
			return this;
		}

		public Builder setFields(final List<KPIField> fields)
		{
			this.fields = fields;
			return this;
		}

		public Builder setCompareOffset(final Duration compareOffset)
		{
			this.compareOffset = compareOffset;
			return this;
		}

		public Builder setESSearchIndex(final String esSearchIndex)
		{
			this.esSearchIndex = esSearchIndex;
			return this;
		}

		public Builder setESSearchTypes(final String esSearchTypes)
		{
			this.esSearchTypes = esSearchTypes;
			return this;
		}

		public Builder setESQuery(final String query)
		{
			esQuery = query;
			return this;
		}

		public Builder setDefaultTimeRange(final Duration defaultTimeRange)
		{
			this.defaultTimeRange = defaultTimeRange == null ? Duration.ZERO : defaultTimeRange;
			return this;
		}

		public Builder setPollIntervalSec(final int pollIntervalSec)
		{
			this.pollIntervalSec = pollIntervalSec;
			return this;
		}

	}
}
