-- TODO : Fixing Dx (currently D8 only works)
//I took about 2 months to create this engine, if you are trying to modify it using reverse engineering without my permission, it's very annoying, be kind and leave plz, I am trying hard to make this engine free for all (paid features because I can't eat likes)... just for your knowledge:
// I have been without internet (free mode) for more than 8 months except little times,
// I don't get chance until now to try real ChatGPT or midjourney and other AI's..., thanks for messenger bots : MixerBox, NoGTP..

//I don't update this file...😬
//Anyway remember , when you want to add new element, copy any item like TextItem for example, then edit it with the properties that you need, then create file for it "items/item.json", also write it's name in getDefualt() method in java and getName() method....
the json file will contain the default values of your item..
when you need new property, just add it's name in the map.json in it's section like :
"New Section":"Property 1,",
and it's type in types.json, then to body properties...

!!!!!! => don't forget updating Editor load method after adding new element....(you can understand by reading it)
switch(TYPE){
    case "BOX":
    ....
    ....
    ....
    case "New Element":
    return new Element(Context);
}
+ ===> CodeGenerator update getType method....

you can add new sections or add it to previous one...
• the elements in dragControl.json which separated by "," is elements that can be edited by [ < ] and [ > ] which appears in the right of the property in properties list.
• I used separator in Utils class to avoid removing interal path if I use getLastPathSegmant() ,.. ignore if you don't understand...
•   Classes:
     • Adapters
        - EditorField :
        used to create field for properties like x,y,z..., it take Context, Editor variable, name and type, the types defined in "types.json" file.
        - EditorValue :
        useless class because at first I want to make the EditorValue split classes with it's own logics.
        - ImagesSelectorAdapter :
        used to create images selector dialog using it's static method show() which take context,onselect interface, and editor...see how to use it in EditorField image case, you can edit it to be ListView if you want.
        - Properties
        used to create Properties at the left of EditorActivity....
        - Section
        used to create section for the properties like All x,y,z is set at one section.
        the sections created based on "map.json" file.
        - NumbersDialog
        used to show floating numbers input dialog to edit values like x,y,z ... etc
     • Helpers
        - FileUtil
        it's a class from sketchware, contains many methods to manage files.
        - Project
        simple class contains methods to manage engine project.
        - PropertySet
        it's a class extends HashMap<String, Object> and contains addition methods for more access to it's fields and also contains getPropertySet(View v) method that used to get properties from the items, which return empty PropertySet when there's no properties.
        - ScaleGesture
        used in the editor to enable Zoom In/Out.
     • Items
        - BoxBody :
        it's the box item which implements EditorItem interface which used to detect if item is editor item and contains it's default methods.
        - Editor :
        the main editor class , contains all methods from loading methods (load/loadFromPath) until Undo/Redo methods and so many methods to manage the Editor.
        - EditorItem :
        an interface contains the default methods for every EditorItem like box.
    - finally there is Utils class which contains many methods which is used in many tasks like setImageFromFile and image repeat methods and..etc.
    - ValuesEditor
    useless in this project, but it's input dialog that used in My Visual Scripting System, I add it by miss then I fix it.
•   every item will have it's own file like box.json, it contains it's own additional properties and default properties is file contains global properties.
• "types.json" contains the value types like float, string,..etc and it will be used in "EditorField" class to create View based on it.
