
package fi.soveltia.liferay.gsearch.core.impl.results.item;

import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.service.DLAppService;
import com.liferay.document.library.kernel.util.DLUtil;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalService;
import com.liferay.dynamic.data.mapping.service.DDMStructureLocalServiceUtil;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.search.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.portlet.PortletRequest;
import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import fi.soveltia.liferay.gsearch.core.api.constants.ParameterNames;
import fi.soveltia.liferay.gsearch.core.api.query.context.QueryContext;
import fi.soveltia.liferay.gsearch.core.api.results.item.ResultItemBuilder;
import fi.soveltia.liferay.gsearch.core.impl.util.GSearchUtil;

/**
 * DLFileEntry item type result builder.
 *
 * @author Petteri Karttunen
 */
@Component(
	immediate = true, 
	service = ResultItemBuilder.class
)
public class DLFileEntryItemBuilder
	extends BaseResultItemBuilder implements ResultItemBuilder {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean canBuild(Document document) {
		return _NAME.equals(document.getString(Field.ENTRY_CLASS_NAME));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getLink(QueryContext queryContext, Document document)
		throws Exception {

		HttpServletRequest httpServletRequest =
			(HttpServletRequest)queryContext.getParameter(
				ParameterNames.HTTP_SERVLET_REQUEST);

		PortletRequest portletRequest = GSearchUtil.getPortletRequest(
			httpServletRequest);

		boolean viewResultsInContext = isViewInContext(queryContext);

		String assetPublisherPageURL = getAssetPublisherPageURL(queryContext);

		if (viewResultsInContext && (assetPublisherPageURL != null) &&
			(portletRequest != null)) {

			StringBundler sb = new StringBundler();

			ThemeDisplay themeDisplay =
				(ThemeDisplay)portletRequest.getAttribute(
					WebKeys.THEME_DISPLAY);

			// DL findEntry generated by DL renderer seems not to produce
			// correct url.
			// Using AssetPublisher page.

			Layout layout = GSearchUtil.getLayoutByFriendlyURL(
				portletRequest, assetPublisherPageURL);

			String assetPublisherInstanceId =
				GSearchUtil.findDefaultAssetPublisherInstanceId(layout);

			AssetEntry assetEntry = _assetEntryLocalService.getEntry(
					DLFileEntry.class.getName(),
					document.getLong(Field.ENTRY_CLASS_PK));
							
			sb.append(PortalUtil.getLayoutFriendlyURL(layout, themeDisplay));
			sb.append("/-/asset_publisher/");
			sb.append(assetPublisherInstanceId);
			sb.append("/document/id/");
			sb.append(assetEntry.getEntryId());

			return sb.toString();
		}

		return getDirectLink(httpServletRequest, document);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws Exception
	 */
	@Override
	public Map<String, String> getMetadata(
			QueryContext queryContext, Document document)
		throws Exception {

		Map<String, String> metaData = new HashMap<>();

		String mimeType = document.getString("mimeType");

		// Format

		metaData.put("format", translateMimetype(mimeType));

		// Size

		metaData.put("size", getSize(document));

		// Image metadata

		if (mimeType.startsWith("image_")) {
			setImageMetadata(queryContext, document, metaData);
		}

		return metaData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getThumbnail(QueryContext queryContext, Document document)
		throws Exception {

		HttpServletRequest httpServletRequest =
			(HttpServletRequest)queryContext.getParameter(
				ParameterNames.HTTP_SERVLET_REQUEST);

		PortletRequest portletRequest = GSearchUtil.getPortletRequest(
			httpServletRequest);

		if (portletRequest == null) {
			StringBuilder sb = new StringBuilder();

			sb.append(getDirectLink(httpServletRequest, document));
			sb.append("?imageThumbnail=1");

			return sb.toString();
		}

		ThemeDisplay themeDisplay = (ThemeDisplay)portletRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		long entryClassPK = document.getLong(Field.ENTRY_CLASS_PK);

		FileEntry fileEntry = _dLAppService.getFileEntry(entryClassPK);

		return DLUtil.getThumbnailSrc(fileEntry, themeDisplay);
	}

	protected String getDirectLink(
		HttpServletRequest httpServletRequest, Document document) {

		StringBundler sb = new StringBundler();

		sb.append(PortalUtil.getPortalURL(httpServletRequest));
		sb.append("/documents/");
		sb.append(document.getString(Field.SCOPE_GROUP_ID));
		sb.append("/");
		sb.append(document.getString(Field.FOLDER_ID));
		sb.append("/");
		sb.append(document.getString("path"));

		return sb.toString();
	}

	/**
	 * Beautify file size
	 *
	 * @param size
	 * @param locale
	 * @return
	 */
	protected String getSize(Document document) {
		long size = document.getLong("size");

		StringBundler sb = new StringBundler();

		if (size >= MBYTES) {
			sb.append(
				Math.round(size / (float)MBYTES)
			);
			sb.append(
				" MB"
			);
		}
		else if (size >= KBYTES) {
			sb.append(
				Math.round(size / (float)KBYTES)
			);
			sb.append(
				" KB"
			);
		}
		else {
			sb.append(
				1
			);
			sb.append(
				" KB"
			);
		}

		return sb.toString();
	}

	/**
	 * Get index translated field name for a Tikaraw metadata field.
	 *
	 * @param key
	 * @return
	 * @throws Exception
	 */
	protected String getTikaRawMetadataField(
			QueryContext queryContext, String key)
		throws Exception {

		StringBundler sb = new StringBundler();

		sb.append("ddm__text__");
		sb.append(String.valueOf(getTikaRawStructureId(queryContext)));
		sb.append("__TIFF_IMAGE_");
		sb.append(key);
				
		
		return sb.toString();
	}

	/**
	 * Get the id for structure holding image metadata ("TIKARAWMETADATA") Using
	 * static map here to reduce DB queries.
	 *
	 * @param queryContext
	 * @return
	 * @throws Exception
	 */
	protected long getTikaRawStructureId(QueryContext queryContext)
		throws Exception {

		long companyId = (long)queryContext.getParameter(
			ParameterNames.COMPANY_ID);

		if ((TIKARAW_STRUCTURE_ID_MAP == null) ||
			(TIKARAW_STRUCTURE_ID_MAP.get(companyId) == null)) {

			DynamicQuery structureQuery =
				_ddmStructureLocalService.dynamicQuery();

			structureQuery.add(
				RestrictionsFactoryUtil.eq("structureKey", "TIKARAWMETADATA"));
			structureQuery.add(
				RestrictionsFactoryUtil.eq("companyId", companyId));

			List<DDMStructure> structures =
				DDMStructureLocalServiceUtil.dynamicQuery(structureQuery);

			DDMStructure structure = structures.get(0);

			TIKARAW_STRUCTURE_ID_MAP = new HashMap<>();

			TIKARAW_STRUCTURE_ID_MAP.put(companyId, structure.getStructureId());
		}

		return TIKARAW_STRUCTURE_ID_MAP.get(companyId);
	}

	/**
	 * Set image metadata.
	 *
	 * @param metaData
	 * @throws Exception
	 */
	protected void setImageMetadata(
			QueryContext queryContext, Document document,
			Map<String, String> metaData)
		throws Exception {

		// Dimensions

		StringBundler sb = new StringBundler();

		sb.append(document.getString(getTikaRawMetadataField(queryContext, "WIDTH")));
		sb.append(" x ");
		sb.append(
			document.getString(getTikaRawMetadataField(queryContext, "LENGTH")));
		sb.append(" px");

		metaData.put("dimensions", sb.toString());
	}

	/**
	 * Translate mimetype for UI
	 *
	 * @param mimeType
	 * @return
	 */
	protected String translateMimetype(String mimeType) {
		if (mimeTypes.containsKey(mimeType)) {
			return mimeTypes.get(mimeType);
		}
		else if (mimeType.startsWith("application_")) {
			return mimeType.split("application_")[1];
		}
		else if (mimeType.startsWith("image_")) {
			return mimeType.split("image_")[1];
		}
		else if (mimeType.startsWith("text_")) {
			return mimeType.split("text_")[1];
		}
		else if (mimeType.startsWith("video_")) {
			return mimeType.split("video_")[1];
		}

		return mimeType;
	}

	protected static final long KBYTES = 1024;

	protected static final long MBYTES = 1024 * 1024;

	protected static Map<String, String> mimeTypes =
		new HashMap<String, String>() {
			{
				put(
					"application_vnd.openxmlformats-officedocument.presentationml.presentation",
					"pptx");
				put(
					"application_vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					"xlsx");
				put(
					"application_vnd.openxmlformats-officedocument.wordprocessingml.document",
					"docx");

				put("application_vnd.ms-excel", "xls");
				put("application_vnd.ms-powerpoint", "ppt");
				put("application_vnd.ms-word", "doc");

				put("application_vnd.oasis.opendocument.presentation", "odp");
				put("application_vnd.oasis.opendocument.spreadsheet", "ods");
				put("application_vnd.oasis.opendocument.text", "odt");
			}
		};

	protected Map<Long, Long> TIKARAW_STRUCTURE_ID_MAP = null;

	private static final String _NAME = DLFileEntry.class.getName();

	@Reference
	private AssetEntryLocalService _assetEntryLocalService;

	@Reference
	private DDMStructureLocalService _ddmStructureLocalService;

	@Reference
	private DLAppService _dLAppService;

}