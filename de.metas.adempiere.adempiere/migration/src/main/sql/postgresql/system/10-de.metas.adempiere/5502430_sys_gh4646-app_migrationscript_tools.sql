
DROP FUNCTION IF EXISTS public.add_migrationscript(character varying);
DROP FUNCTION IF EXISTS public.migrationscript_ignore(character varying);

CREATE FUNCTION public.migrationscript_ignore(projectAndFileName character varying)
  RETURNS void AS
$BODY$
 INSERT INTO public.AD_MigrationScript (
	ad_client_id, ad_migrationscript_id, ad_org_id, created, createdby, description, developername, 
	isactive, name, 
	projectname, reference, releaseno, scriptroll, status, url, updated, updatedby, isapply, filename, script
 ) VALUES (
	0, nextval('ad_migrationscript_seq') , 0, now(), 100, 'Inserted by migrationscript_ignore with parameter projectAndFileName='||projectAndFileName, NULL, 
	'Y', replace (projectAndFileName,'/','->'),
    left (projectAndFileName, position('/' in projectAndFileName )-1 ), NULL, '1' , NULL, 'CO', NULL, now(), 100, 'N', right (projectAndFileName, length(projectAndFileName)-position('/' in projectAndFileName)), NULL);
$BODY$
  LANGUAGE sql VOLATILE
  COST 100;
COMMENT ON FUNCTION public.migrationscript_ignore(character varying) IS 'Inserts the given script into the AD_MigrationScript table, so it will be ignored on future rollouts.
usage example:
select migrationscript_ignore(''configuration/changingpartnerwindow.sql'');

Please keep in sync with http://docs.metasfresh.org/sql_collection/migrationscript_helper_functions.html
';

DROP FUNCTION IF EXISTS public.migrationscript_ignoreNewProjectName(character varying, character varying);
CREATE FUNCTION public.migrationscript_ignoreNewProjectName(
	oldProjectName character varying, 
	newProjectName character varying)
  RETURNS void AS
$BODY$
 INSERT INTO public.AD_MigrationScript (
	ad_client_id, ad_migrationscript_id, ad_org_id, created, createdby, 
	description, 
	developername, 
	isactive, 
	name, 
	projectname, 
	reference, releaseno, scriptroll, status, url, updated, updatedby, isapply, 
	filename, 
	script
 )
 SELECT
	0, nextval('ad_migrationscript_seq') , 0, now(), 100, 
	'Inserted by migrationscript_ignoreChangedProject with parameters oldProjectName='||oldProjectName||'; newProjectName='||newProjectName,  -- description
	NULL, 
	'Y', 
	regexp_replace(name, '^'||oldProjectName||'->', newProjectName||'->'), -- Name
    newProjectName, 
	reference, releaseno, scriptroll, status, url, updated, updatedby, isapply, 
	fileName, -- filename
	NULL
 FROM public.ad_migrationscript
 WHERE projectname=oldProjectName
 ON CONFLICT DO NOTHING
 ;
$BODY$
  LANGUAGE sql VOLATILE
  COST 100;
  COMMENT ON FUNCTION public.migrationscript_ignoreNewProjectName(character varying, character varying) IS 'For all AD_MigrationScript records with the given oldProjectName, 
this function inserts additional AD_MigrationScript records that are similar, but have newProjectName in their name and projectname columns.

usage example:
select migrationscript_ignoreNewProjectName(''projectnameWithTypo'', ''fixedName'');

Note that it only inserts if no AD_MigrationScript with the respective new name already exists.

Please keep in sync with http://docs.metasfresh.org/sql_collection/migrationscript_helper_functions.html
';
